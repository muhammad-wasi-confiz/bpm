package io.takari.bpm.actions;

import io.takari.bpm.misc.CoverageIgnore;

public class FireOnElementInterceptorsAction implements Action {

    private static final long serialVersionUID = 1L;

    private final String definitionId;
    private final String elementId;

    public FireOnElementInterceptorsAction(String definitionId, String elementId) {
        this.definitionId = definitionId;
        this.elementId = elementId;
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public String getElementId() {
        return elementId;
    }

    @Override
    @CoverageIgnore
    public String toString() {
        return "FireOnElementInterceptorsAction [definitionId=" + definitionId + ", elementId=" + elementId + "]";
    }
}
