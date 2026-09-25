package com.sondhan.model;
import java.time.LocalDateTime;
/** Topic 3: Maps to a row in the searches SQLite table. */
public class SearchHistory {
    private int id; private int userId; private String inputType;
    private String originalInput; private String claim; private String verdict;
    private int confidence; private String explanation;
    private String sourcesJson;
    private LocalDateTime createdAt;
    public int getId() { return id; } public void setId(int v) { id=v; }
    public int getUserId() { return userId; } public void setUserId(int v) { userId=v; }
    public String getInputType() { return inputType; } public void setInputType(String v) { inputType=v; }
    public String getOriginalInput() { return originalInput; } public void setOriginalInput(String v) { originalInput=v; }
    public String getClaim() { return claim; } public void setClaim(String v) { claim=v; }
    public String getVerdict() { return verdict; } public void setVerdict(String v) { verdict=v; }
    public int getConfidence() { return confidence; } public void setConfidence(int v) { confidence=v; }
    public String getExplanation() { return explanation; } public void setExplanation(String v) { explanation=v; }
    public String getSourcesJson() { return sourcesJson; } public void setSourcesJson(String v) { sourcesJson=v; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime v) { createdAt=v; }
}