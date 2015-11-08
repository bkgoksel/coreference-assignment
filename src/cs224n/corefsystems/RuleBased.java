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
		// TODO Auto-generated method stub.

	}

	@Override
	public List<ClusteredMention> runCoreference(Document doc) {
        ArrayList<ClusteredMention> clusteredMentions = new ArrayList<ClusteredMention>();
        Map<Mention, Integer> mentionToIndex = new HashMap<Mention, Integer>();

        //1st pass exact string match + exact head match (exclude pronouns)
        exactStringMatchPass(doc, clusteredMentions, mentionToIndex);
        //2nd pass hobbs algorithm for regular pronouns (resolving gender and plurals)
        appositivePass(clusteredMentions, mentionToIndex);
        //Quoted lines pass. "I", "my", "myself", "me", etc all refer to speaker.

        //3rd pass gender and plurals

        //4th loose string matching
		return clusteredMentions;
	}

    private void exactStringMatchPass(Document doc, ArrayList<ClusteredMention> clusteredMentions, Map<Mention, Integer> mentionToIndex) {
        Map<String, Entity> stringToEntity = new HashMap<String, Entity>();
        for (Mention m : doc.getMentions()) {
            if (!Pronoun.isSomePronoun(m.headWord())) { //if it's not a pronoun
                String m_gloss = m.gloss();
                if (stringToEntity.containsKey(m_gloss)) {
                    ClusteredMention toAdd = m.markCoreferent(stringToEntity.get(m_gloss));
                    mentionToIndex.put(m, clusteredMentions.size()); //new index is curr size
                    clusteredMentions.add(toAdd);
                } else {
                    ClusteredMention newCluster = m.markSingleton();
                    mentionToIndex.put(m, clusteredMentions.size());
                    clusteredMentions.add(newCluster);
                    stringToEntity.put(m_gloss, newCluster.entity);
                }
            } else {
                ClusteredMention newCluster = m.markSingleton();
                mentionToIndex.put(m, clusteredMentions.size());
                clusteredMentions.add(newCluster);
            }
        }
    }

    private void appositivePass(ArrayList<ClusteredMention> clusteredMentions, Map<Mention, Integer> mentionToIndex) {
        for (int i = 0; i < clusteredMentions.size(); i++) {
            List<Integer> candidates = getCandidates(clusteredMentions, i);
            Mention curr = clusteredMentions.get(i).mention;
            //loop through sorted candidates
            for (int j : candidates) {
                Mention candidate = clusteredMentions.get(j).mention;
                //check appositive
                boolean changed = false;
                if (curr.sentence == candidate.sentence) {
                    Queue<Tree<String>> q = new LinkedList<Tree<String>>();
                    Tree<String> sentParse = curr.sentence.parse;
                    q.add(sentParse);
                    while(!q.isEmpty()) {
                        Tree<String> currTree = q.remove();
                        String rule = "";
                        for (Tree<String> child : currTree.getChildren()) {
                            rule += child.getLabel() + " ";
                        }
                        if (currTree.getLabel().equals("NP") && rule.equals("NP , NP ")) {
                            List<String> candLabels = currTree.getChildren().get(0).getYield();
                            List<String> currLabels = currTree.getChildren().get(2).getYield();
                            if (candLabels.equals(candidate.text()) && currLabels.equals(curr.text())) {
                                mergeClusteredMention(clusteredMentions.get(i), clusteredMentions.get(j), mentionToIndex, clusteredMentions);
                                changed = true;
                                System.out.println(curr.sentence);
                                System.out.println(curr.gloss() + " | " + candidate.gloss());
                                System.out.println("hooray!");
                                break;
                            }
                        }
                        for (Tree<String> child : currTree.getChildren()) {
                            q.add(child);
                        }
                    }
                }
                if (changed) {
                    break;
                }
            }
        }
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

    private void mergeClusteredMention(ClusteredMention a, ClusteredMention b, Map<Mention, Integer> mentionToIndex, ArrayList<ClusteredMention> clusteredMentions) {
        if (a.entity == b.entity) {
            //do nothing
        } else {
            for (Mention m : b.entity.mentions) {
                m.removeCoreference();
                ClusteredMention newCluster = m.markCoreferent(a.entity);
                clusteredMentions.set(mentionToIndex.get(m), newCluster);
            }
            b.entity.mentions.clear();
        }
    }

    private List<Integer> getCandidates(ArrayList<ClusteredMention> clusteredMentions, int index) {
        Map<Entity, Integer> entityToIndex = new HashMap<Entity, Integer>();
        for (int j = index - 1; j >= 0; j--) {
            entityToIndex.put(clusteredMentions.get(j).entity, j); //find first index for each entity
        }
        List<Integer> indices = new ArrayList<Integer>(); //sorting it so that we are traversing r to l
        for (int val : entityToIndex.values()) {
            indices.add(val);
        }
        Collections.sort(indices, Collections.reverseOrder()); //make that we are going high to low
        return indices;
    }

}
