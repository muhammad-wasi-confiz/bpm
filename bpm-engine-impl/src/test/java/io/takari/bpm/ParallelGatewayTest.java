package io.takari.bpm;

import io.takari.bpm.model.EndEvent;
import io.takari.bpm.model.ExclusiveGateway;
import io.takari.bpm.model.InclusiveGateway;
import io.takari.bpm.model.IntermediateCatchEvent;
import io.takari.bpm.model.ParallelGateway;
import io.takari.bpm.model.ProcessDefinition;
import io.takari.bpm.model.SequenceFlow;
import io.takari.bpm.model.StartEvent;
import java.util.Arrays;
import java.util.UUID;
import org.junit.Ignore;
import org.junit.Test;

public class ParallelGatewayTest extends AbstractEngineTest {

    /**
     * start --> gw1 --> ev --> gw2 --> end
     */
    @Test
    public void testSingleEvent() throws Exception {
        String processId = "test";
        deploy(new ProcessDefinition(processId, Arrays.asList(
                new StartEvent("start"),
                new SequenceFlow("f1", "start", "gw1"),
                new ParallelGateway("gw1"),
                    new SequenceFlow("f2", "gw1", "ev", "false"),
                    new IntermediateCatchEvent("ev", "ev"),
                    new SequenceFlow("f3", "ev", "gw2"),
                new InclusiveGateway("gw2"),
                new SequenceFlow("f4", "gw2", "end"),
                new EndEvent("end")
        )));

        // ---

        String key = UUID.randomUUID().toString();
        getEngine().start(key, processId, null);

        // ---

        getEngine().resume(key, "ev", null);

        // ---

        assertActivations(key, processId,
                "start",
                "f1",
                "gw1",
                "f2",
                "ev",
                "f3",
                "gw2",
                "f4",
                "end");
        assertNoMoreActivations();
    }
    
    /**
     * start --> gw1 --> ev --> end
     */
    @Test
    public void testWithoutJoin() throws Exception {
        String processId = "test";
        deploy(new ProcessDefinition(processId, Arrays.asList(
                new StartEvent("start"),
                new SequenceFlow("f1", "start", "gw1"),
                new ParallelGateway("gw1"),
                    new SequenceFlow("f2", "gw1", "ev", "false"),
                    new IntermediateCatchEvent("ev", "ev"),
                    new SequenceFlow("f3", "ev", "end"),
                    new EndEvent("end")
        )));

        // ---

        String key = UUID.randomUUID().toString();
        getEngine().start(key, processId, null);

        // ---

        getEngine().resume(key, "ev", null);

        // ---

        assertActivations(key, processId,
                "start",
                "f1",
                "gw1",
                "f2",
                "ev",
                "f3",
                "end");
        assertNoMoreActivations();
    }
    
    /**
     * start --> gw1 --> gw2 --> end
     *              \  /
     *               --
     */
    @Test
    @Ignore
    public void testPartialInJoin() throws Exception {
        String processId = "test";
        deploy(new ProcessDefinition(processId, Arrays.asList(
                new StartEvent("start"),
                new SequenceFlow("f1", "start", "gw1"),
                new ExclusiveGateway("gw1"),
                    new SequenceFlow("f2", "gw1", "gw2", "true"),
                    new SequenceFlow("f3", "gw1", "gw2", "false"),
                    new ParallelGateway("gw2"),
                        new SequenceFlow("f4", "gw2", "end"),
                        new EndEvent("end")
        )));

        // ---

        String key = UUID.randomUUID().toString();
        getEngine().start(key, processId, null);

        // ---

        assertActivations(key, processId,
                "start",
                "f1",
                "gw1",
                "f2",
                "gw2",
                "f4",
                "end");
        assertNoMoreActivations();
    }
}
