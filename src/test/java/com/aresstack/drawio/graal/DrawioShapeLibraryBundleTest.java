package com.aresstack.drawio.graal;

import java.time.Duration;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Check the pinned production shape library (grapheditor Shapes.js, mxBasic.js,
 * mxBpmnShape2.js) is packaged and valid for the restricted GraalJS runtime.
 *
 * <p>Executing it requires the original mxGraph core bundle of the graph
 * pipeline (#9); the end-to-end rendering tests live with that pipeline.
 */
class DrawioShapeLibraryBundleTest {

    private final ClasspathScriptLoader scripts = new ClasspathScriptLoader();

    @Test
    void parsesInTheRestrictedRuntimeWithoutExecuting() {
        String library = scripts.load("drawio-shape-library.js");
        // A function expression is compiled but its body is not run.
        String source = "var compiled = function () {\n" + library + "\n}; typeof compiled;";

        try (GraalJsRuntime runtime = new GraalJsRuntime(Duration.ofSeconds(30))) {
            ScriptExecutionResult result = runtime.execute("drawio-shape-library-parse.js", source,
                    new DrawioHost(resourceId -> {
                        throw new IllegalArgumentException("disabled");
                    }));
            assertTrue(result.isSuccessful(), result.getErrorMessage());
        }
    }

    @Test
    void registersStandardBasicAndBpmnShapes() {
        String library = scripts.load("drawio-shape-library.js");
        Set<String> literal = new TreeSet<String>();
        Matcher matcher = Pattern.compile("registerShape\\('([^']+)'").matcher(library);
        while (matcher.find()) {
            literal.add(matcher.group(1));
        }
        for (String expected : new String[] {"process", "document", "note", "cube", "cylinder3",
                "parallelogram", "trapezoid", "step", "card", "tape", "dataStorage",
                "internalStorage", "umlActor", "umlLifeline", "umlFrame", "table", "tableRow",
                "flexArrow", "singleArrow", "doubleArrow", "mxgraph.bpmn.gateway2",
                "mxgraph.bpmn.event", "mxgraph.bpmn.shape"}) {
            assertTrue(literal.contains(expected), expected + " not registered in " + literal);
        }
        for (String constant : new String[] {"mxShapeBasicRect2.prototype.cst.RECT2",
                "mxShapeBpmn2Task.prototype.cst.TASK", "mxShapeBpmn2Swimlane.prototype.cst.SWIMLANE",
                "mxShapeBpmn2Data.prototype.cst.DATA"}) {
            assertTrue(library.contains("registerShape(" + constant), constant);
        }
        assertTrue(library.contains("RECT2 : 'mxgraph.basic.rect'")
                || library.contains("RECT2: 'mxgraph.basic.rect'")
                || library.contains("RECT2 :'mxgraph.basic.rect'"),
                "mxgraph.basic.rect must be registered before mxBpmnShape2.js extends it");
        assertTrue(library.indexOf("mxShapeBasicRect2") < library.indexOf("function mxShapeBpmn2Task("),
                "Shapes.js must precede mxBpmnShape2.js");
    }
}
