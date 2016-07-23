package io.takari.bpm;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.api.interceptors.ElementEvent;
import io.takari.bpm.api.interceptors.ExecutionInterceptor;
import io.takari.bpm.api.interceptors.InterceptorStartEvent;

public class ExecutionInterceptorHolder {

    private final List<ExecutionInterceptor> interceptors = new CopyOnWriteArrayList<>();

    public void addInterceptor(ExecutionInterceptor i) {
        interceptors.add(i);
    }

    public void fireOnStart(String processBusinessKey, String processDefinitionId, UUID executionId, Map<String, Object> variables)
            throws ExecutionException {

        InterceptorStartEvent ev = new InterceptorStartEvent(processBusinessKey, processDefinitionId, executionId, variables);
        for (ExecutionInterceptor i : interceptors) {
            i.onStart(ev);
        }
    }

    public void fireOnSuspend() throws ExecutionException {
        for (ExecutionInterceptor i : interceptors) {
            i.onSuspend();
        }
    }

    public void fireOnResume() throws ExecutionException {
        for (ExecutionInterceptor i : interceptors) {
            i.onResume();
        }
    }

    public void fireOnFinish(String processBusinessKey) throws ExecutionException {
        for (ExecutionInterceptor i : interceptors) {
            i.onFinish(processBusinessKey);
        }
    }

    public void fireOnError(String processBusinessKey, Throwable cause) throws ExecutionException {
        for (ExecutionInterceptor i : interceptors) {
            i.onError(processBusinessKey, cause);
        }
    }

    public void fireOnElement(String processBusinessKey, String processDefinitionId, UUID executionId, String elementId)
            throws ExecutionException {

        ElementEvent ev = new ElementEvent(processBusinessKey, processDefinitionId, executionId, elementId);
        for (ExecutionInterceptor i : interceptors) {
            i.onElement(ev);
        }
    }
}