package com.sondhan.model;

import java.util.List;

/**
 * Holds the result of an image backtrack / reverse image search.
 */
public class ImageBacktrackResult {

    public static class Match {
        public final String title;
        public final String url;
        public final String source;   // domain / site name
        public final String snippet;  // short description

        public Match(String title, String url, String source, String snippet) {
            this.title   = title;
            this.url     = url;
            this.source  = source;
            this.snippet = snippet;
        }
    }

    private String       summary;          // Claude-generated origin summary
    private String       earliestSource;   // best-guess earliest origin
    private List<Match>  matches;          // individual reverse-search results
    private boolean      originFound;

    public String       getSummary()       { return summary; }
    public void         setSummary(String v){ summary = v; }
    public String       getEarliestSource(){ return earliestSource; }
    public void         setEarliestSource(String v){ earliestSource = v; }
    public List<Match>  getMatches()       { return matches; }
    public void         setMatches(List<Match> v){ matches = v; }
    public boolean      isOriginFound()    { return originFound; }
    public void         setOriginFound(boolean v){ originFound = v; }
}
