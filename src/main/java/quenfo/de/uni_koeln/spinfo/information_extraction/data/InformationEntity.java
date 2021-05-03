package quenfo.de.uni_koeln.spinfo.information_extraction.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import weka.gui.ResultHistoryPanel;

@Data
@EqualsAndHashCode(of = { "startLemma", "singleWordEntity", "lemmaArray" })
@ToString(of = { "lemmaExpression" })
public class InformationEntity { // TODO protected/abstract?

	@DatabaseField(generatedId = true)
	private Integer id;

	@DatabaseField(canBeNull = false, foreign = true, uniqueCombo = true)
	@Setter(AccessLevel.NONE)
	private ExtractionUnit parent;
	
	@DatabaseField
	@Setter(AccessLevel.NONE)
	private IEType type;

	// Index des ersten Lemmatas im Satz
	@Setter(AccessLevel.NONE)
	private int firstIndex;

	// erstes Wort des Kompetenz-/Tool-Ausdrucks
	@DatabaseField
	@Setter(AccessLevel.NONE)
	private String startLemma;

	// ist 'true' wenn der Ausdruck aus nur einem Wort (startLemma) besteht
	@DatabaseField
	@Setter(AccessLevel.NONE)
	private boolean singleWordEntity;

	@DatabaseField(dataType = DataType.SERIALIZABLE)
	@Setter(AccessLevel.NONE)
	private String[] lemmaArray;

	@DatabaseField(uniqueCombo = true)
	@Setter(AccessLevel.NONE)
	//@Getter(AccessLevel.NONE)
	private String lemmaExpression;

	// Modifizierer (z.B. 'zwingend erforderlich')
	@DatabaseField
	private String modifier;

	// expandierte Koordinationen im Ausdruck (für Evaluierung)
	@Setter(AccessLevel.NONE)
	private List<String> coordinations;

	

	private static StringJoiner sj;

	private double conf;

	/**
	 * default constructor for object relational mapping
	 */
	public InformationEntity() {

	}

	/**
	 * constructor for known information entities (without extractionUnit)
	 * 
	 * @param startLemma
	 * @param isSingleWordEntity
	 * @param type
	 */
	public InformationEntity(String startLemma, boolean isSingleWordEntity, IEType type) {
		this.startLemma = startLemma;
		this.singleWordEntity = isSingleWordEntity;
		this.type = type;
		if (isSingleWordEntity) {
			this.lemmaArray = new String[1];
			this.lemmaArray[0] = startLemma;
			this.lemmaExpression = startLemma;
		}
	}

	

	public InformationEntity(String startLemma, boolean isSingleWordEntity, int firstIndex, 
			IEType type, ExtractionUnit parent) {
		this(startLemma, isSingleWordEntity, type);
		this.firstIndex = firstIndex;
		this.parent = parent;
	}

	public InformationEntity(String startLemma, boolean isSingleWordEntity, 
			IEType type, ExtractionUnit parent) {
		this(startLemma, isSingleWordEntity, -1, type, parent);
	}
	
	
	
	/////////////////////////////////////////////////////////////////////////////////
	
	public void setCoordinates(String resolvedCoo) {
		List<String> gold = Arrays.asList(resolvedCoo.split(";"));
		this.coordinations = new ArrayList<String>();
		for (String g : gold) {
			this.coordinations.add(g.trim());
		}
	}

	/**
	 * transforms util.arraylist to array and assigns array to field
	 * 
	 * @param lemmaArrayList
	 */
	public void setLemmaArrayList(List<String> lemmaArrayList) {
		this.lemmaArray = new String[lemmaArrayList.size()];
		lemmaArrayList.toArray(this.lemmaArray);

		InformationEntity.sj = new StringJoiner(" ");
		for (String l : lemmaArray)
			sj.add(l);

		this.lemmaExpression = sj.toString();
	}

	/**
	 * get copy(!) of lemma array as util.arraylist
	 * 
	 * @return
	 */
	public List<String> getLemmaArrayList() {
		return new ArrayList<>(Arrays.asList(lemmaArray));
	}

	public void setLemmaArray(String[] lemmaArray) {
		this.lemmaArray = lemmaArray;

		InformationEntity.sj = new StringJoiner(" ");
		for (String l : lemmaArray)
			sj.add(l);
		this.lemmaExpression = sj.toString();
	}

	/**
	 * 
	 * @author Christine Schaefer
	 * 
	 * @param usedPattern
	 */
	public Double setConf(List<Pattern> usedPattern) {
		this.conf = 0d;
		double product = 0d;
		
		//System.out.println(usedPattern);

		List<Double> confValue = new ArrayList<Double>();

		for (Pattern p : usedPattern) {
			confValue.add(1 - p.getConf()); // (1 - Conf(P)) = Wahrscheinlichkeit für die Fehlerhaftigkeit
		}

		//System.out.println(confValue);
		for (int i = 1; i <= confValue.size(); i++) {
			// Wie berechne ich das Produkt aller Elemente der Liste confValue?
			if (product == 0d) {
				product = confValue.get(i - 1);
			} else {
				product = product * confValue.get(i - 1);
			}
		}
		conf = 1 - product;
		//System.out.println(conf);
		return conf;

	}
	
}
