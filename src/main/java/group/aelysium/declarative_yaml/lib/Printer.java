package group.aelysium.declarative_yaml.lib;

import group.aelysium.declarative_yaml.annotations.Node;

import java.util.HashMap;
import java.util.Map;

public class Printer {
    protected int indentSpaces = 4;
    private boolean indentComments = true;
    private String lineSeparator = "\n\n\n";
    private boolean injecting = false;
    private Map<String, String> pathReplacements = new HashMap<>(0);
    private Map<String, String> commentReplacements = new HashMap<>(0);
    public Printer() {}

    public int indentSpaces() {
        return this.indentSpaces;
    }
    public Printer indentSpaced(int indentSpaces) {
        this.indentSpaces = indentSpaces;
        return this;
    }

    public boolean indentComments() {
        return this.indentComments;
    }
    public Printer indentComments(boolean indentComments) {
        this.indentComments = indentComments;
        return this;
    }

    public String lineSeparator() {
        return this.lineSeparator;
    }
    public Printer lineSeparator(String lineSeparator) {
        this.lineSeparator = lineSeparator;
        return this;
    }

    public Map<String, String> pathReplacements() {
        return this.pathReplacements;
    }
    public Printer pathReplacements(Map<String, String> pathReplacements) {
        this.pathReplacements = pathReplacements;
        return this;
    }

    public Map<String, String> commentReplacements() {
        return this.commentReplacements;
    }
    public Printer commentReplacements(Map<String, String> commentReplacements) {
        this.commentReplacements = commentReplacements;
        return this;
    }

    public boolean injecting() {
        return this.injecting;
    }
    public Printer injecting(boolean injecting) {
        this.injecting = injecting;
        return this;
    }
}
