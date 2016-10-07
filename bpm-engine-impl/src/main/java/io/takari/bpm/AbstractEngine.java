package io.takari.bpm;

import io.takari.bpm.actions.*;
import io.takari.bpm.api.BpmnError;
import io.takari.bpm.api.Engine;
import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.api.NoEventFoundException;
import io.takari.bpm.api.interceptors.ExecutionInterceptor;
import io.takari.bpm.event.Event;
import io.takari.bpm.event.EventPersistenceManager;
import io.takari.bpm.lock.LockManager;
import io.takari.bpm.persistence.PersistenceManager;
import io.takari.bpm.planner.Planner;
import io.takari.bpm.state.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public abstract class AbstractEngine implements Engine {

    private static final Logger log = LoggerFactory.getLogger(AbstractEngine.class);

    protected abstract IndexedProcessDefinitionProvider getProcessDefinitionProvider();

    protected abstract UuidGenerator getUuidGenerator();

    protected abstract Planner getPlanner();

    protected abstract Executor getExecutor();

    protected abstract PersistenceManager getPersistenceManager();

    protected abstract ExecutionInterceptorHolder getInterceptorHolder();

    protected abstract EventPersistenceManager getEventManager();

    protected abstract LockManager getLockManager();

    protected abstract Configuration getConfiguration();

    @Override
    public void start(String processBusinessKey, String processDefinitionId, Map<String, Object> variables) throws ExecutionException {
        IndexedProcessDefinitionProvider pdp = getProcessDefinitionProvider();
        IndexedProcessDefinition pd = pdp.getById(processDefinitionId);

        UuidGenerator idg = getUuidGenerator();
        UUID instanceId = idg.generate();

        ProcessInstance state = StateHelper.createInitialState(getExecutor(), instanceId, processBusinessKey, pd, variables);

        LockManager lm = getLockManager();
        lm.lock(processBusinessKey);

        try {
            runLockSafe(state);
        } catch (Exception e) {
            // TODO move to the executor?
            getInterceptorHolder().fireOnError(processBusinessKey, e);
            throw e;
        } finally {
            lm.unlock(processBusinessKey);
        }
    }

    @Override
    public void resume(String processBusinessKey, String eventName, Map<String, Object> variables) throws ExecutionException {
        LockManager lm = getLockManager();
        lm.lock(processBusinessKey);

        try {
            EventPersistenceManager em = getEventManager();
            Collection<Event> evs = em.find(processBusinessKey, eventName);

            if (evs == null || evs.isEmpty()) {
                throw new NoEventFoundException("No event '%s' found for process '%s'", eventName, processBusinessKey);
            } else if (evs.size() > 1) {
                StringBuilder b = new StringBuilder();
                for (Event e : evs) {
                    b.append(e).append(",");
                }
                throw new ExecutionException("Non-unique event name in process '%s': %s. Events: %s", processBusinessKey, eventName, b);
            }

            Event e = evs.iterator().next();
            resumeLockSafe(e, variables);
        } catch (Exception e) {
            // TODO move to the executor?
            getInterceptorHolder().fireOnError(processBusinessKey, e);
            throw e;
        } finally {
            lm.unlock(processBusinessKey);
        }
    }

    @Override
    public void resume(UUID eventId, Map<String, Object> variables) throws ExecutionException {
        EventPersistenceManager em = getEventManager();
        Event ev = em.get(eventId);
        if (ev == null) {
            throw new NoEventFoundException("No event '%s' found", eventId);
        }

        String businessKey = ev.getProcessBusinessKey();

        LockManager lm = getLockManager();
        lm.lock(businessKey);

        try {
            resumeLockSafe(ev, variables);
        } catch (Exception e) {
            // TODO move to the executor?
            getInterceptorHolder().fireOnError(businessKey, e);
            throw e;
        } finally {
            lm.unlock(businessKey);
        }
    }

    @Override
    public void addInterceptor(ExecutionInterceptor i) {
        getInterceptorHolder().addInterceptor(i);
    }

    private void resumeLockSafe(Event e, Map<String, Object> variables) throws ExecutionException {
        String businessKey = e.getProcessBusinessKey();
        String eventName = e.getName();

        UUID eid = e.getExecutionId();
        log.debug("resumeLockSafe ['{}', '{}'] -> got '{}'", businessKey, eventName, eid);

        EventPersistenceManager em = getEventManager();
        if (e.isExclusive()) {
            // exclusive event means that only one event from the group of
            // events can happen. The rest of the events must be removed.
            em.clearGroup(businessKey, e.getScopeId());
        } else {
            em.remove(e.getId());
        }

        PersistenceManager pm = getPersistenceManager();
        ProcessInstance state = pm.get(eid);
        if (state == null) {
            throw new ExecutionException("No execution '%s' found for the process '%s'", eid, businessKey);
        }

        // enable the execution
        state = state.setStatus(ProcessStatus.RUNNING);

        // apply the external variables (no additional attributes provided)
        state = StateHelper.applyVariables(state, null, variables);

        // fire the interceptors
        // TODO move to the planner?
        state = getExecutor().eval(state, Arrays.asList(new FireOnResumeInterceptorsAction()));

        // process event-to-command mappings (e.g. add next command of the flow
        // to the stack)
        state = pushEventCommands(state, e);

        runLockSafe(state);
    }

    private void runLockSafe(ProcessInstance state) throws ExecutionException {
        log.debug("runLockSafe ['{}'] -> started...", state.getBusinessKey());

        while (state.getStatus() == ProcessStatus.RUNNING) {
            if (log.isTraceEnabled()) {
                StateHelper.dump(state);
            }

            List<Action> actions = getPlanner().eval(state);
            state = getExecutor().eval(state, actions);
        }

        // fire the interceptors

        // TODO move to the planner?
        ProcessStatus status = state.getStatus();
        BpmnError raisedError = BpmnErrorHelper.getRaisedError(state.getVariables());

        if (raisedError != null) {
            state = getExecutor().eval(state, Arrays.asList(new FireOnFailureInterceptorsAction(raisedError.getErrorRef())));

            log.debug("runLockSafe ['{}'] -> failed with '{}'", state.getBusinessKey(), raisedError.getErrorRef(), raisedError.getCause());

            Configuration cfg = getConfiguration();
            if (cfg.isThrowExceptionOnErrorEnd()) {
                throw new ExecutionException("Process finished with an error end event: " + raisedError.getErrorRef(), raisedError.getCause());
            }
        } else if (status == ProcessStatus.SUSPENDED) {
            state = getExecutor().eval(state, Arrays.asList(new FireOnSuspendInterceptorsAction()));
            log.debug("runLockSafe ['{}'] -> suspended", state.getBusinessKey());
        } else if (status == ProcessStatus.FINISHED) {
            state = getExecutor().eval(state, Arrays.asList(new FireOnFinishInterceptorsAction()));
            log.debug("runLockSafe ['{}'] -> done", state.getBusinessKey());
        }

        if (log.isTraceEnabled()) {
            StateHelper.dump(state);
        }
    }

    private static ProcessInstance pushEventCommands(ProcessInstance state, Event ev) {
        Events events = state.getEvents();
        state = events.pushCommands(state, ev.getScopeId(), ev.getId());

        if (ev.isExclusive()) {
            // exclusive event: clear the whole scope
            events = events.clearScope(ev.getScopeId());
        } else {
            // non-exclusive event: remove only the current event
            events = events.removeEvent(ev.getScopeId(), ev.getId());
        }

        return state.setEvents(events);
    }

}
