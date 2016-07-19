package io.takari.bpm.xml.bpmnjs.model;

import java.io.Serializable;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(namespace = Constants.MODEL_NS, name = "definitions")
public class XmlDefinitions implements Serializable {
	
	private static final long serialVersionUID = 1L;
    
    private XmlProcess process;

    public XmlProcess getProcess() {
        return process;
    }

    public void setProcess(XmlProcess process) {
        this.process = process;
    }
}