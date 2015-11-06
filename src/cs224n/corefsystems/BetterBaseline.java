package cs224n.corefsystems;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;


import cs224n.coref.ClusteredMention;
import cs224n.coref.Document;
import cs224n.coref.*;
import cs224n.util.Pair;

public class BetterBaseline implements CoreferenceSystem {

	@Override
	public void train(Collection<Pair<Document, List<Entity>>> trainingData) {
		// TODO Auto-generated method stub

	}

	@Override
	public List<ClusteredMention> runCoreference(Document doc) {
		// TODO Auto-generated method stub
	
	ArrayList<ClusteredMention> clusters = new ArrayList<ClusteredMention>();
	for (Mention m : doc.getMentions()) {
        if (m.gloss().equals("God the Protector")) {
        System.out.println(m.sentence.parse);
	System.out.println(m.parse);
        System.out.println(m.beginIndexInclusive + " "+m.endIndexExclusive);
        System.out.println(m.gloss());
}
     clusters.add(m.markSingleton());
}

	return clusters;
	}

}
