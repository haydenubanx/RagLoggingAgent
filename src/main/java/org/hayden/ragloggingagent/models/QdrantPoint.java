package org.hayden.ragloggingagent.models;

import java.util.Map;

public class QdrantPoint {
    public int id;
    public double[] vector;
    public Map<String, Object> payload;
}