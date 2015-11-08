package cs224n.corefsystems;

import java.util.Collection;
import java.util.List;
import java.util.*;

import cs224n.coref.ClusteredMention;
import cs224n.coref.Document;
import cs224n.coref.Entity;
import cs224n.coref.*;

import cs224n.util.*;

import cs224n.ling.*;

public class RuleBased implements CoreferenceSystem {

	@Override
	public void train(Collection<Pair<Document, List<Entity>>> trainingData) {
		// TODO Auto-generated method stub

	}

	@Override
	public List<ClusteredMention> runCoreference(Document doc) {
        ArrayList<ClusteredMention> mentions = new ArrayList<ClusteredMention>();
        Map<Mention, Integer> mentionToIndex = new HashMap<Mention, Integer>();
        SortedSet<Integer> firstIndex = new TreeSet<Integer>();

        //1st pass exact string match + exact head match (exclude pronouns)
        exactStringMatchPass(doc, mentions, mentionToIndex, firstIndex);
        //2nd pass hobbs algorithm for regular pronouns (resolving gender and plurals)
        appositivePass(mentions, mentionToIndex, firstIndex);
        //Quoted lines pass. "I", "my", "myself", "me", etc all refer to speaker.

        //3rd pass gender and plurals

        //4th loose string matching
		return mentions;
	}

    private void exactStringMatchPass(Document doc, ArrayList<ClusteredMention> mentions, Map<Mention, Integer> mentionToIndex, SortedSet<Integer> firstIndex) {
        Map<String, Entity> stringToEntity = new HashMap<String, Entity>();
        for (Mention m : doc.getMentions()) {
            if (!Pronoun.isSomePronoun(m.headWord())) { //if it's not a pronoun
                String m_gloss = m.gloss();
                if (stringToEntity.containsKey(m_gloss)) {
                    ClusteredMention toAdd = m.markCoreferent(stringToEntity.get(m_gloss));
                    mentionToIndex.put(m, mentions.size()); //new index is curr size
                    mentions.add(toAdd);
                } else {
                    ClusteredMention newCluster = m.markSingleton();
                    mentionToIndex.put(m, mentions.size());
                    firstIndex.add(mentions.size());
                    mentions.add(newCluster);
                    stringToEntity.put(m_gloss, newCluster.entity);
                }
            } else {
                ClusteredMention newCluster = m.markSingleton();
                mentionToIndex.put(m, mentions.size());
                firstIndex.add(mentions.size());
                mentions.add(newCluster);
            }
        }
    }

    private void appositivePass(ArrayList<ClusteredMention> mentions, Map<Mention, Integer> mentionToIndex, SortedSet<Integer> firstIndex) {
        for (int i : firstIndex) {
            ClusteredMention curr = mentions.get(i);
            for (int j : firstIndex) {
                if (j >= i) {
                    break;
                } //guarantees that j is less than i 
                ClusteredMention prev = mentions.get(j);
                if (curr.mention.sentence == prev.mention.sentence) { //they must be in the same sentence
                    Tree<String> sentParse = curr.mention.sentence.parse;
                    int currParseIndex = curr.mention.parse.getUniqueIndex();
                    int prevParseIndex = prev.mention.parse.getUniqueIndex();
                    System.out.println(j + " " +  i);
                    for (Pair<String, Integer> trav : sentParse.getTraversalBetween(currParseIndex, prevParseIndex)) {
                        System.out.println(trav.getFirst());
                    }
                }
            }
        }
        //stuff
    }

    /* UTILITY */
    /**
      * Merge two entities
      * @param a Entity to merge into
      * @param b Entity that will end up being empty
      */
    private Entity mergeEntities(Entity a, Entity b) {
        for (Mention m : b.mentions) {
            m.removeCoreference();
        }
        a = a.addAll(b.mentions);
        b.mentions.clear();
        return a;
    }

    private void mergeClusteredMention(ClusteredMention a, ClusteredMention b, Map<Mention, Integer> mentionToIndex, ArrayList<ClusteredMention> mentions) {
        if (a.entity == b.entity) {
            //do nothing
        } else {
            for (Mention m : b.entity.mentions) {
                m.removeCoreference();
                ClusteredMention newCluster = m.markCoreferent(a.entity);
                mentions.set(mentionToIndex.get(m), newCluster);
            }
            b.entity.mentions.clear();
        }
    }
}
