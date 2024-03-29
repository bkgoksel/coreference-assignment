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

  private static final int N_PASSES = 10; // INCREMENT THIS EVERY TIME YOU ADD A PASS
                                         // ALSO USEFUL TO DEBUG STEP BY STEP
  private static final int PRONOUN_PASS = 9; // the index of the pass where we check for pronouns(Used for separate candidates selection alg.)

	@Override
	public void train(Collection<Pair<Document, List<Entity>>> trainingData) {
		// TODO Auto-generated method stub.

	}

	@Override
	public List<ClusteredMention> runCoreference(Document doc) {
       Map<Mention, Integer> mentionToIndex = new HashMap<Mention, Integer>();
       List<ClusteredMention> clusteredMentions = buildSingletonClusters(doc, mentionToIndex);
       for (int pass = 0; pass < N_PASSES; pass++) {
           for (int i = 0; i < clusteredMentions.size(); i++) {
               List<Integer> candidates;
               if (pass == PRONOUN_PASS) {
                   candidates = getPronominalCandidates(clusteredMentions, i);
               } else {
                   candidates = getCandidates(clusteredMentions, i);
               }
               //loop through sorted candidates
               for (int j : candidates) {
                   //check for whatever the current pass is asking you to check
                   if (shouldMerge(pass, i, j, clusteredMentions)) { // i is index of curr, j is index of candidate
                       mergeClusteredMention(clusteredMentions.get(i), clusteredMentions.get(j), mentionToIndex, clusteredMentions);
                       break;
                   }
               }
           }
       }
		   return clusteredMentions;
	}

  /* SIEVE METHODS */

    private boolean exactStringMatch(Mention curr, Mention candidate) {
        if (!Pronoun.isSomePronoun(curr.headWord())) {
            return curr.gloss().toLowerCase().equals(candidate.gloss().toLowerCase());
        }
        return false;
    }      

    private boolean appositiveMatch(Mention curr, Mention candidate) {
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
                      return true;
                    }
                }
                for (Tree<String> child : currTree.getChildren()) {
                    q.add(child);
                }
            }
        }
        return false;
    }

    private boolean exactHeadWordMatch(Mention curr, Mention candidate) {
        if(!Pronoun.isSomePronoun(curr.headWord())) {
            return curr.headWord().toLowerCase().equals(candidate.headWord().toLowerCase());
        }
        return false;
    }
    
    private boolean acronymMatch(Mention curr, Mention candidate) {
        if(!Pronoun.isSomePronoun(curr.headWord())) {
            if(curr.headToken().posTag().equals("NNP") && candidate.headToken().posTag().equals("NNP")) {
                return (curr.gloss().equals(acronymize(candidate.gloss())) || candidate.gloss().equals(acronymize(curr.gloss()))); 
            }
        }
        return false;
    }

    // checks whether the current is completely contained in the candidate
    // is not performing great(decreasing score)
    private boolean wordContainmentMatch(Mention curr, Mention candidate) {
        if (!Pronoun.isSomePronoun(curr.headWord())) {
            return candidate.text().containsAll(curr.text());
        }
        return false;
    }

    // TODO: Implement
    private boolean relaxedHeadWordMatch(Mention curr, Mention candidate) {
        if (!Pronoun.isSomePronoun(curr.headWord())) {
            if (curr.headToken().nerTag().equals(candidate.headToken().nerTag())) {
                return false;
            }
        }
        return false;
    }

    private boolean headWordLemmaMatch(Mention curr, Mention candidate) {
        if(!Pronoun.isSomePronoun(curr.headWord())) {
            if (curr.headToken().lemma().equals(candidate.headToken().lemma())) {
               Pair<Boolean, Boolean> genderAgreement = Util.haveGenderAndAreSameGender(curr, candidate);
               Pair<Boolean, Boolean> numberAgreement = Util.haveNumberAndAreSameNumber(curr, candidate);
               return (!genderAgreement.getFirst() || genderAgreement.getSecond()) && (!numberAgreement.getFirst() || numberAgreement.getSecond());
            }
        }
        return false;
    }

    private boolean sameSentenceFirstSecondPersonMatch(Mention curr, Mention candidate) {
        if ((curr.sentence == candidate.sentence) && Pronoun.isSomePronoun(curr.headWord()) && Pronoun.isSomePronoun(candidate.headWord())) {
            Pronoun currPronoun = Pronoun.getPronoun(curr.headWord());
            Pronoun candidatePronoun = Pronoun.getPronoun(candidate.headWord());
            if(currPronoun != null && candidatePronoun != null && (currPronoun.speaker == Pronoun.Speaker.FIRST_PERSON || currPronoun.speaker == Pronoun.Speaker.SECOND_PERSON)) {
                return (currPronoun.speaker == candidatePronoun.speaker) && (currPronoun.plural == candidatePronoun.plural);
            }
        }
        return false;
    }


    private boolean pronounMatch(Mention curr, Mention candidate, Entity candidateEntity) {
        if (Pronoun.isSomePronoun(curr.headWord())) {
            Pronoun currPronoun = Pronoun.getPronoun(curr.headWord());
            Pair<Boolean, Boolean> genderAgreement = Util.haveGenderAndAreSameGender(curr, candidateEntity);
            Pair<Boolean, Boolean> numberAgreement = Util.haveNumberAndAreSameNumber(curr, candidateEntity);
            return (!genderAgreement.getFirst() || genderAgreement.getSecond()) && (numberAgreement.getSecond());
        }
        return false;
    }

    private boolean quoteMatch(Mention curr, Mention candidate, Entity candidateEntity) {
        if (curr.headToken().isQuoted()) {
            for (Mention m : candidateEntity.mentions) {
                String speaker = curr.headToken().speaker();
                if (m.gloss().contains(speaker) && curr.sentence == m.sentence) {
                    return true;
                }
            }
            return false;
        } else {
            return false;
        }
    }


    /* UTILITY */

    private boolean shouldMerge(int passNumber, int fixedIndex, int candidateIndex, List<ClusteredMention> clusteredMentions) {
        ClusteredMention currClusteredMention = clusteredMentions.get(fixedIndex);
        ClusteredMention candidateClusteredMention = clusteredMentions.get(candidateIndex);

        Mention curr = currClusteredMention.mention;
        Mention candidate = candidateClusteredMention.mention;

        Entity currEntity = currClusteredMention.entity;
        Entity candidateEntity = candidateClusteredMention.entity;

        switch (passNumber) {
            case 0:
                return exactStringMatch(curr, candidate);
            case 1:
                return appositiveMatch(curr, candidate);
            case 2:
                return acronymMatch(curr, candidate);
            case 3:
                return exactHeadWordMatch(curr, candidate);
            case 4:
                break;
                //return wordContainmentMatch(curr, candidate);
            case 5:
                return relaxedHeadWordMatch(curr, candidate);
            case 6:
                return headWordLemmaMatch(curr, candidate);
            case 7:
                return sameSentenceFirstSecondPersonMatch(curr, candidate);
            case 8:
                return quoteMatch(curr, candidate, candidateEntity);
            case 9:
                return pronounMatch(curr, candidate, candidateEntity);
        }
        
        return false;
    }

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

    private void mergeClusteredMention(ClusteredMention a, ClusteredMention b, Map<Mention, Integer> mentionToIndex, List<ClusteredMention> clusteredMentions) {
        if (a.entity != b.entity) {
            for (Mention m : b.entity.mentions) {
                m.removeCoreference();
                ClusteredMention newCluster = m.markCoreferent(a.entity);
                clusteredMentions.set(mentionToIndex.get(m), newCluster);
            }
            b.entity.mentions.clear();
        }
    }

    private List<Integer> getCandidates(List<ClusteredMention> clusteredMentions, int index) {
          Map<Entity, Integer> entityToIndex = new HashMap<Entity, Integer>();
          List<Integer> first = new ArrayList<Integer>();
          for (int j = index - 1; j >= 0; j--) {
              first.add(j);
              entityToIndex.put(clusteredMentions.get(j).entity, j); //find first index for each entity
          }
          Set<Entity> added = new HashSet<Entity>();
          List<Integer> finalIndices = new ArrayList<Integer>();
          for (int elem : first) {
              Entity elemEntity = clusteredMentions.get(elem).entity;
              if (!added.contains(elemEntity)) {
                  int bestInteger = entityToIndex.get(elemEntity);
                  finalIndices.add(bestInteger);
                  added.add(elemEntity);
              }
          }
          return finalIndices;
    }


    private List<Integer> getPronominalCandidates(List<ClusteredMention> clusteredMentions, int index) {
        Map<Entity, Integer> entityToIndex = new HashMap<Entity, Integer>();
        for (int j = index - 1; j >= 0; j--) {
            entityToIndex.put(clusteredMentions.get(j).entity, j); //find first index for each entity
        }
        //first pass get entity to first endices
        List<Integer> sentenceOrdered = new ArrayList<Integer>();
        List<Integer> temp = new ArrayList<Integer>();
        Sentence currSentence = clusteredMentions.get(index).mention.sentence;
        for (int j = index - 1; j >= 0; j--) {
            Mention curr = clusteredMentions.get(j).mention;
            if (currSentence == curr.sentence) {
                temp.add(j);
            } else {
                currSentence = curr.sentence;
                Collections.reverse(temp);
                sentenceOrdered.addAll(temp);
            }
        }
        Set<Entity> added = new HashSet<Entity>();
        List<Integer> finalIndices = new ArrayList<Integer>();
        for (int elem : sentenceOrdered) {
            Entity elemEntity = clusteredMentions.get(elem).entity;
            if (!added.contains(elemEntity)) {
                int bestInteger = entityToIndex.get(elemEntity);
                finalIndices.add(bestInteger);
                added.add(elemEntity);
            }
        }
        return finalIndices;
    }

    private List<ClusteredMention> buildSingletonClusters(Document doc, Map<Mention, Integer> mentionToIndex) {
        List<ClusteredMention> clusteredMentions = new ArrayList<ClusteredMention>();
        for (Mention m : doc.getMentions()) {
            ClusteredMention newCluster = m.markSingleton();
            mentionToIndex.put(m, clusteredMentions.size());
            clusteredMentions.add(newCluster);
        }
        return clusteredMentions;
    }

    String acronymize(String str) {
        String result = "";
        for(int i=0; i<str.length(); i++) {
            char curChar = str.charAt(i);
            if(Character.isUpperCase(curChar)) {
                result += curChar;
            }
        }
        return result;
    }
}
