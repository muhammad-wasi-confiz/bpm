package io.takari.bpm.context;

import io.takari.bpm.actions.Action;
import io.takari.bpm.actions.SetVariableAction;
import io.takari.bpm.actions.UnsetVariableAction;
import io.takari.bpm.api.ExecutionContext;
import io.takari.bpm.state.Variables;

import java.util.*;

public class ExecutionContextImpl implements ExecutionContext {

    private static final long serialVersionUID = 1L;

    private final Variables source;
    private final Map<String, Change> changes = new HashMap<>();

    public ExecutionContextImpl(Variables source) {
        this.source = source;
    }

    @Override
    public Object getVariable(String key) {
        return currentValue(key);
    }

    @Override
    public Map<String, Object> getVariables() {
        return source.asMap();
    }

    @Override
    public void setVariable(String key, Object value) {
        changes.put(key, new Change(ChangeType.SET, value));
    }

    @Override
    public boolean hasVariable(String key) {
        Change c = changes.get(key);
        if (c != null && c.getType() == ChangeType.SET) {
            return true;
        }

        return source.hasVariable(key);
    }

    @Override
    public void removeVariable(String key) {
        changes.put(key, new Change(ChangeType.UNSET, null));
    }

    @Override
    public Set<String> getVariableNames() {
        return source.getVariableNames();
    }

    private Object currentValue(String key) {
        Change c = changes.get(key);
        if (c != null) {
            switch (c.getType()) {
            case SET: {
                return c.getValue();
            }
            case UNSET: {
                return null;
            }
            }
        }

        return source.getVariable(key);
    }

    public List<Action> toActions() {
        List<Action> actions = new ArrayList<>();

        for (Map.Entry<String, Change> e : changes.entrySet()) {
            String key = e.getKey();
            Change c = e.getValue();

            if (c.getType() == ChangeType.SET) {
                actions.add(new SetVariableAction(key, c.getValue()));
            } else if (c.getType() == ChangeType.UNSET) {
                actions.add(new UnsetVariableAction(key));
            } else {
                // TODO better exception type
                throw new RuntimeException("Unsupported change type: " + c.getType());
            }
        }

        return actions;
    }
}