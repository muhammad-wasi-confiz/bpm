package io.takari.bpm.xml.activiti;

import io.takari.bpm.model.AbstractElement;
import io.takari.bpm.model.BoundaryEvent;
import io.takari.bpm.model.CallActivity;
import io.takari.bpm.model.EndEvent;
import io.takari.bpm.model.EventBasedGateway;
import io.takari.bpm.model.ExclusiveGateway;
import io.takari.bpm.model.ExpressionType;
import io.takari.bpm.model.InclusiveGateway;
import io.takari.bpm.model.IntermediateCatchEvent;
import io.takari.bpm.model.ParallelGateway;
import io.takari.bpm.model.ProcessDefinition;
import io.takari.bpm.model.SequenceFlow;
import io.takari.bpm.model.SequenceFlow.ExecutionListener;
import io.takari.bpm.model.ServiceTask;
import io.takari.bpm.model.StartEvent;
import io.takari.bpm.model.SubProcess;
import io.takari.bpm.model.VariableMapping;
import io.takari.bpm.xml.Parser;
import io.takari.bpm.xml.ParserException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class ActivitiParser implements Parser {

    public static final String TYPE = "activiti/bpmn";
    private static final Logger log = LoggerFactory.getLogger(ActivitiParser.class);

    @Override
    public ProcessDefinition parse(InputStream in) throws ParserException {
        if (in == null) {
            throw new NullPointerException("Input cannot be null");
        }

        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            SAXParser p = spf.newSAXParser();

            Handler h = new Handler();
            p.parse(in, h);

            return h.process;
        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new ParserException("Parsing error", e);
        }
    }

    private static final class Handler extends DefaultHandler {
        
        private static final class Item {
            private final String name;
            private final String processId;
            private final Collection<AbstractElement> children;

            public Item(String processId, String name, Collection<AbstractElement> children) {
                this.processId = processId;
                this.name = name;
                this.children = children;
            }
        }
        
        private String id;
        private String name;
        private String processId;
        private String processName;
        private String attachedToRef;
        private String errorRef;
        private String sourceRef;
        private String targetRef;
        private String messageRef;
        private String timeDate;
        private String timeDuration;
        private String calledElement;
        private StringBuilder text;

        private ProcessDefinition process;
        private Collection<AbstractElement> children;
        private final Stack<Item> items = new Stack<>();
        
        private Collection<ExecutionListener> listeners;
        private Set<VariableMapping> in;
        private Set<VariableMapping> out;

        @Override
        public void startElement(String uri, String qName, String localName, Attributes attributes) throws SAXException {
            log.debug("startElement ['{}']", localName);
            
            localName = stripNamespace(localName);

            switch (localName) {  
                case "process":
                    processId = attributes.getValue("id");
                    processName = attributes.getValue("name");
                    children = new ArrayList<>();
                    break;
                    
                case "subProcess":
                    name = attributes.getValue("name");                    
                    items.push(new Item(processId, name, children));
                    processId = attributes.getValue("id");
                    children = new ArrayList<>();
                    break;

                case "startEvent":
                    id = attributes.getValue("id");
                    StartEvent ev = new StartEvent(id);
                    children.add(ev);
                    break;

                case "callActivity":
                    id = attributes.getValue("id");
                    name = attributes.getValue("name");
                    calledElement = attributes.getValue("calledElement");
                    break;

                case "boundaryEvent":
                    id = attributes.getValue("id");
                    attachedToRef = attributes.getValue("attachedToRef");
                    break;

                case "errorEventDefinition":
                    errorRef = attributes.getValue("errorRef");
                    break;

                case "endEvent":
                    id = attributes.getValue("id");

                    break;

                case "sequenceFlow":
                    id = attributes.getValue("id");
                    name = attributes.getValue("name");
                    sourceRef = attributes.getValue("sourceRef");
                    targetRef = attributes.getValue("targetRef");
                    break;

                case "conditionExpression":
                    text = new StringBuilder();
                    break;

                case "exclusiveGateway":
                    id = attributes.getValue("id");
                    ExclusiveGateway eg = new ExclusiveGateway(id, attributes.getValue("default"));
                    children.add(eg);
                    break;
                    
                case "parallelGateway":
                    id = attributes.getValue("id");
                    ParallelGateway pg = new ParallelGateway(id);
                    children.add(pg);
                    break;
                    
                case "inclusiveGateway":
                    id = attributes.getValue("id");
                    InclusiveGateway ig = new InclusiveGateway(id);
                    children.add(ig);
                    break;

                case "serviceTask":
                    id = attributes.getValue("id");
                    name = attributes.getValue("name");
                    
                    String simple = attributes.getValue("expression");
                    String delegate = attributes.getValue("delegateExpression");

                    // fallback to activiti's namespace
                    if (simple == null && delegate == null) {
                        simple = attributes.getValue("activiti:expression");
                        delegate = attributes.getValue("activiti:delegateExpression");
                    }

                    ExpressionType type = ExpressionType.NONE;
                    String expr = null;

                    if (simple != null) {
                        type = ExpressionType.SIMPLE;
                        expr = simple;
                    } else if (delegate != null) {
                        type = ExpressionType.DELEGATE;
                        expr = delegate;
                    }

                    ServiceTask st = new ServiceTask(id, type, expr);
                    st.setName(name);
                    children.add(st);
                    break;

                case "executionListener":
                    if (listeners == null) {
                        listeners = new ArrayList<>();
                    }

                    String event = attributes.getValue("event");
                    
                    String s = null;
                    ExpressionType t = ExpressionType.NONE;
                    
                    String expression = attributes.getValue("expression");
                    String delegateExpression = attributes.getValue("delegateExpression");
                    if (expression != null) {
                        s = expression;
                        t = ExpressionType.SIMPLE;
                    } else if (delegateExpression != null) {
                        s = delegateExpression;
                        t = ExpressionType.DELEGATE;
                    }

                    ExecutionListener sel = new ExecutionListener(event, t, s);
                    listeners.add(sel);
                    break;

                case "in":
                    if (in == null) {
                        in = new HashSet<>();
                    }
                    in.add(parseVariableMapping(attributes));
                    break;
                    
                case "out":
                    if (out == null) {
                        out = new HashSet<>();
                    }
                    out.add(parseVariableMapping(attributes));
                    break;
                    
                case "eventBasedGateway":
                    id = attributes.getValue("id");
                    
                    EventBasedGateway ebg = new EventBasedGateway(id);
                    children.add(ebg);
                    break;
                    
                case "messageEventDefinition":
                    messageRef = attributes.getValue("messageRef");
                    break;

                case "intermediateCatchEvent":
                    id = attributes.getValue("id");
                    break;
                    
                case "timeDate":
                    text = new StringBuilder();
                    break;
                    
                case "timeDuration":
                    text = new StringBuilder();
                    break;
            }
        }

        private VariableMapping parseVariableMapping(Attributes attributes) {
            String source = attributes.getValue("source");
            String sourceExpression = attributes.getValue("sourceExpression");
            String target = attributes.getValue("target");
            return new VariableMapping(source, sourceExpression, target);
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (text != null) {
                text.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String qName, String localName) throws SAXException {
            log.debug("endElement ['{}']", localName);
            
            localName = stripNamespace(localName);

            switch (localName) {
                case "process":
                    process = new ProcessDefinition(processId, children,
                            Collections.singletonMap(ProcessDefinition.SOURCE_TYPE_ATTRIBUTE, TYPE));
                    process.setName(processName);
                    
                    children = null;
                    break;
                    
                case "subProcess":
                    SubProcess p = new SubProcess(processId, children);
                    
                    Item i = items.pop();
                    processId = i.processId;
                    children = i.children;
                    
                    p.setName(i.name);

                    children.add(p);
                    
                    break;

                case "boundaryEvent":
                    BoundaryEvent be = new BoundaryEvent(id, attachedToRef, errorRef, timeDuration);
                    children.add(be);
                    
                    attachedToRef = null;
                    errorRef = null;
                    timeDuration = null;
                    break;

                case "sequenceFlow":
                    String expr = text != null ? text.toString().trim() : null;

                    ExecutionListener[] l = null;
                    if (listeners != null) {
                        l = listeners.toArray(new ExecutionListener[listeners.size()]);
                    }

                    SequenceFlow sf = new SequenceFlow(id, sourceRef, targetRef, expr, l);
                    sf.setName(name);
                    children.add(sf);

                    name = null;
                    sourceRef = null;
                    targetRef = null;
                    text = null;
                    listeners = null;
                    break;

                case "endEvent":
                    EndEvent ee = new EndEvent(id, errorRef);
                    children.add(ee);

                    errorRef = null;
                    break;
                    
                case "callActivity":
                    CallActivity ca = new CallActivity(id, calledElement, in, out);
                    ca.setName(name);                    
                    children.add(ca);
                    
                    calledElement = null;
                    in = null;
                    name = null;
                    out = null;
                    break;
                
                case "intermediateCatchEvent":
                    IntermediateCatchEvent ice = new IntermediateCatchEvent(id, messageRef, timeDate, timeDuration);
                    children.add(ice);
                    
                    messageRef = null;
                    timeDate = null;
                    timeDuration = null;
                    break;
                    
                case "timeDate":
                    timeDate = text.toString();
                    text = null;
                    break;
                    
                case "timeDuration":
                    timeDuration = text.toString();
                    text = null;
                    break;
            }
        }
    }
    
    private static String stripNamespace(String s) {
        if (s == null) {
            return s;
        }
        
        int i = s.indexOf(":");
        if (i >= 0 && i + 1 < s.length()) {
            return s.substring(i + 1);
        }
        
        return s;
    }

    @Override
    public String toString() {
        return "Legacy Activiti's XML Parser";
    }
}
