package com.sondhan.model;
import java.util.List;

public class FactCheckResult {
    public static class Source {
        public final String title, url;
        public Source(String title, String url) { this.title=title; this.url=url; }
    }
    private String claim, verdict, explanation;
    private int confidence;
    private List<Source> sources;
    private boolean preloaded;
    private List<String> summary;

    public String getClaim()              { return claim; }
    public void   setClaim(String v)      { claim=v; }
    public String getVerdict()            { return verdict; }
    public void   setVerdict(String v)    { verdict=v; }
    public String getExplanation()        { return explanation; }
    public void   setExplanation(String v){ explanation=v; }
    public int    getConfidence()         { return confidence; }
    public void   setConfidence(int v)    { confidence=v; }
    public List<Source> getSources()      { return sources; }
    public void setSources(List<Source> v){ sources=v; }
    public boolean isPreloaded()          { return preloaded; }
    public void setPreloaded(boolean v)   { preloaded=v; }
    public List<String> getSummary()      { return summary; }
    public void setSummary(List<String> v){ summary=v; }
}