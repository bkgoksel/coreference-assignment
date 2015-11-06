package cs224n.corefsystems;

import java.util.Collection;
import java.util.List;

import cs224n.coref.ClusteredMention;
import cs224n.coref.Document;
import cs224n.coref.Entity;
import cs224n.coref.Mention;
import cs224n.util.Pair;

import java.util.ArrayList;
import java.util.*;

public class OneCluster implements CoreferenceSystem {

	@Override
	public void train(Collection<Pair<Document, List<Entity>>> trainingData) {
		// TODO Auto-generated method stub

	}

	@Override
	public List<ClusteredMention> runCoreference(Document doc) {
		// TODO Auto-generated method stub
        List<ClusteredMention> mentions = new ArrayList<ClusteredMention>();
        ClusteredMention singleCluster = null;
        Entity singleEntity = null; //will get instantiated in the loop
        for (Mention m : doc.getMentions()) {
            String mentionString = m.gloss();
            if (singleEntity == null) {
                singleCluster = m.markSingleton(); //create a new cluster
                singleEntity = singleCluster.entity; //init the entity
                mentions.add(singleCluster);
            } else {
                mentions.add(m.markCoreferent(singleEntity)); //cluster with entity
            }
        }
		return mentions;
	}

}
