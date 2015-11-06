package cs224n.corefsystems;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.*;

import cs224n.util.*;
import cs224n.coref.*;
import cs224n.util.Pair;

public class BetterBaseline implements CoreferenceSystem {

    CounterMap<String, String> headWordPairCounts;
    Counter<String> headWordCounts;

	public void train(Collection<Pair<Document, List<Entity>>> trainingData) {
        headWordCounts = new Counter<String>();
        headWordPairCounts = new CounterMap<String, String>();
        for (Pair<Document, List<Entity>> pair : trainingData) {
            Document doc = pair.getFirst();
            List<Entity> clusters = pair.getSecond();
            List<Mention> mentions = doc.getMentions();
            //Store the counts
            //loop through each entity and mention pair
            for (Entity e : clusters) {
                for (Pair<Mention, Mention> mentionPair : e.orderedMentionPairs()) {
                    //increment head counts for pair, the second one first
                    String firstHead = mentionPair.getFirst().headWord();
                    String secondHead = mentionPair.getSecond().headWord();
                    //final calc will be C(a, b) + C(b, a) / c(a) where a is the curr mention
                    headWordCounts.incrementCount(firstHead, 1.0);
                    headWordCounts.incrementCount(secondHead, 1.0);
                    headWordPairCounts.incrementCount(firstHead, secondHead, 1.0);
                }
            }
        }

	}

	public List<ClusteredMention> runCoreference(Document doc) {
        ArrayList<ClusteredMention> mentions = new ArrayList<ClusteredMention>();
        ArrayList<Entity> entities = new ArrayList<Entity>();
        Map<String, Entity> clusters = new HashMap<String, Entity>();
        
        for (Mention m : doc.getMentions()) {
            //loop through existing mentions in existing entities and remember the best entities
            String currHead = m.headWord();
            String mentionString = m.gloss();
            double currHeadCount = headWordCounts.getCount(currHead);
            Entity bestEntity = null;
            double bestScore = 0.0;
            for (Entity e : entities) {
                for (Mention e_m : e) {
                    //C(a,b) + C(b,a) / C(a)
                    String otherHead = e_m.headWord();
                    double currentScore = (headWordPairCounts.getCount(currHead, otherHead) + headWordPairCounts.getCount(otherHead, currHead)) / (currHeadCount + 1.0);
                    if (currentScore > bestScore) {
                        bestEntity = e;
                        bestScore = currentScore;
                    }
                }
            }
            //if nothing is good then we should group it into it's own cluster
            if (bestEntity != null) {
                //set entity as mention entity
                mentions.add(m.markCoreferent(bestEntity));
            } else if (clusters.containsKey(mentionString)) {
                mentions.add(m.markCoreferent(clusters.get(mentionString)));
            } else {
                ClusteredMention newCluster = m.markSingleton();
                mentions.add(newCluster);
                entities.add(newCluster.entity);
                clusters.put(mentionString, newCluster.entity);
            }
        }
        return mentions;
	}
}
