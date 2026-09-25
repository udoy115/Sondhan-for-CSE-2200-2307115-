package com.sondhan.service;
import com.sondhan.model.FactCheckResult;
import com.sondhan.util.ImageHashUtil;
import java.io.File;
import java.util.*;

/**
 * Topic 2: Initialised on a background thread (ExecutorService in HomeController).
 * Hashes bundled images and builds an in-memory map for instant preloaded matching.
 */
public class PreloadedDatabase {
    private static PreloadedDatabase instance;
    private final Map<String, FactCheckResult> hashToResult = new HashMap<>();
    private boolean initialized = false;

    private static final List<Entry> ENTRIES = List.of(
        new Entry("718014225_1419002733593201_4348281707205913180_n.jpg",
            "Fake comment circulated about Saiyed Abdullah",
            "FALSE", 82,
            "No evidence that Saiyed Abdullah called people ungrateful over electricity price hikes. The post originated from a satirist profile with no credible source.",
            List.of(new FactCheckResult.Source("Rumor Scanner BD","https://rumorscanner.com/fact-check/saiyed-abdullah-fake-comment-claim/208583")),
            List.of("The claim attributes a statement to Saiyed Abdullah without any evidence.",
                    "No credible news source reported this statement.",
                    "The post originated from a self-described satirist account.",
                    "Verdict: FALSE - the claim is completely fabricated.")),
        new Entry("718953356_1465989142222974_2371143075906418461_n.jpg",
            "Case filed against Dr Yunus and Nurjahan Begum",
            "MISLEADING", 72,
            "A case application was filed but dismissed by the court for lack of grounds. No case was actually registered against them.",
            List.of(new FactCheckResult.Source("BDNEWS24.com","https://bangla.bdnews24.com/politics/politics/976b12683a00")),
            List.of("An application was filed but dismissed by the court.",
                    "No case was formally registered.",
                    "Verdict: MISLEADING")),
        new Entry("claim_dhaka_capital.png",
            "Dhaka has been the capital of Bangladesh since independence in 1971",
            "TRUE", 99,
            "Dhaka is the official capital of Bangladesh, designated in Article 5 of the Constitution since independence on December 16, 1971.",
            List.of(new FactCheckResult.Source("Bangladesh Government","https://bangladesh.gov.bd"),
                    new FactCheckResult.Source("Encyclopedia Britannica","https://www.britannica.com/place/Dhaka")),
            List.of("Dhaka is constitutionally designated as the capital of Bangladesh.",
                    "This has been true since independence in 1971.",
                    "Verdict: TRUE")),
        new Entry("claim_5g_health.png",
            "5G towers cause cancer and radiation sickness",
            "FALSE", 97,
            "No scientific evidence links 5G to cancer. 5G uses non-ionizing radio waves that cannot damage DNA. WHO and FDA confirm safety.",
            List.of(new FactCheckResult.Source("WHO","https://www.who.int/news-room/questions-and-answers/item/radiation-5g-mobile-networks-and-health"),
                    new FactCheckResult.Source("FDA","https://www.fda.gov/radiation-emitting-products/cell-phones/scientific-evidence-cell-phone-safety")),
            List.of("5G uses non-ionizing radio frequencies - physically cannot cause cancer.",
                    "WHO, FDA, and peer-reviewed studies confirm 5G safety.",
                    "Verdict: FALSE")),
        new Entry("716690521_122361326474003647_5230761717797101281_n.jpg",
            "Claim about Mohammed Shishir Manir and ex-IGP Mamun case",
            "MISLEADING", 96,
            "While the first 4 claims in the post are true, the last claim is inconsistent. Shishir Manir did not personally handle the case - a member of his legal team did.",
            List.of(new FactCheckResult.Source("BSS News","https://www.bssnews.net/news-flash/290934"),
                    new FactCheckResult.Source("TBS News","https://www.tbsnews.net/bangladesh/court/clemency-ex-igp-mamun-conditional-full-disclosure-july-august-atrocities-ict")),
            List.of("Ex-IGP Mamun was represented by Zayed bin Amzad from Shishir Manir's firm.",
                    "Shishir Manir himself did not appear in court for this case.",
                    "Verdict: MISLEADING"))
    );

    private PreloadedDatabase() {}
    public static PreloadedDatabase getInstance() {
        if (instance == null) instance = new PreloadedDatabase();
        return instance;
    }

    public synchronized void initialize(String dir) {
        if (initialized) return;
        System.out.println("[Preloaded] Initialising from: " + dir);
        for (Entry e : ENTRIES) {
            File f = new File(dir, e.fileName);
            if (!f.exists()) { System.out.println("[Preloaded] Missing: " + f.getAbsolutePath()); continue; }
            try {
                String hash = ImageHashUtil.hashFile(f);
                hashToResult.put(hash, build(e));
                System.out.println("[Preloaded] Registered: " + e.fileName + " -> " + hash.substring(0,12) + "...");
            } catch (Exception ex) { System.out.println("[Preloaded] Hash failed: " + e.fileName); }
        }
        initialized = true;
        System.out.println("[Preloaded] Ready. " + hashToResult.size() + " image(s) registered.");
    }

    public FactCheckResult match(File uploaded) {
        try {
            String hash = ImageHashUtil.hashFile(uploaded);
            FactCheckResult r = hashToResult.get(hash);
            System.out.println("[Preloaded] " + (r != null ? "Match: " + r.getClaim() : "No match."));
            return r;
        } catch (Exception ex) { ex.printStackTrace(); return null; }
    }

    private FactCheckResult build(Entry e) {
        FactCheckResult r = new FactCheckResult();
        r.setClaim(e.claim); r.setVerdict(e.verdict); r.setConfidence(e.confidence);
        r.setExplanation(e.explanation); r.setSources(e.sources);
        r.setSummary(e.summary); r.setPreloaded(true);
        return r;
    }

    private static class Entry {
        final String fileName, claim, verdict, explanation;
        final int confidence;
        final List<FactCheckResult.Source> sources;
        final List<String> summary;
        Entry(String fn,String cl,String vd,int cf,String ex,List<FactCheckResult.Source> src,List<String> sum){
            fileName=fn; claim=cl; verdict=vd; confidence=cf; explanation=ex; sources=src; summary=sum;
        }
    }
}