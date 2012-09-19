package project.riley.predictor;

/** Loads an ARFF file, either sparse or dense format.
 *   
 * @author Scott Sanner (ssanner@gmail.com)
 * 
 * Note: this has been modified to do arff file export and data folds generation, see main().
 */


import java.io.*;
import java.text.NumberFormat;
import java.util.*;

import project.riley.datageneration.DataGeneratorPassiveActive;

public class ArffData {

	public static final int TYPE_UNKNOWN = 0;
	public static final int TYPE_CLASS   = 1; // stored as int
	public static final int TYPE_INT     = 2;
	public static final int TYPE_DOUBLE  = 3;

	public static final String WHITESPACE = "[ \t\r\n\u00A0\uFEFF\u0009\u000B\u000C\u001C\u001D\u001E\u001F]";
	public static final String WHITESPACE_RESPECT_QUOTES = " \t\r\n\u00A0\uFEFF\u0009\u000B\u000C\u001C\u001D\u001E\u001F";

	public String    _filename = null;
	public String    _relation = null;
	public ArrayList<Attribute> _attr = new ArrayList<Attribute>();	
	public ArrayList<DataEntry> _data = new ArrayList<DataEntry>();

	public HashMap<String,Attribute> _attrMap = new HashMap<String,Attribute>();

	public boolean friends = true;
	public int friends_index_start = 4;
	public int friends_index_end = friends_index_start + 1;
	
	public boolean interactions = true;
	public int interactions_index_start = friends_index_end;
	public int interactions_index_end = friends_index_end + 23;
	
	public boolean demographics = true;
	public int demographics_index_start = interactions_index_end;
	public int demographics_index_end = demographics_index_start + DataGeneratorPassiveActive.demographics_types.length;

	public boolean groups = true;
	public int groups_index_start = demographics_index_end;
	public int groups_index_end = groups_index_start + DataGeneratorPassiveActive.topGroupsN;

	public boolean pages = true;
	public int pages_index_start = groups_index_end;
	public int pages_index_end = pages_index_start + DataGeneratorPassiveActive.topPagesN;
	
	public boolean traits = true;
	public int traits_index_start = pages_index_end;
	public int traits_index_end = traits_index_start + DataGeneratorPassiveActive.user_traits.length;

	public boolean outgoingMessages = true;
	public int outgoingMessagesSize = 0;
	public int outgoingMessages_index_start = traits_index_end;
	public int outgoingMessages_index_end = outgoingMessages_index_start + DataGeneratorPassiveActive.topWordsN;

	public boolean incomingMessages = true;
	public int incomingMessagesSize = 0;	
	public int incomingMessages_index_start = outgoingMessages_index_end;
	public int incomingMessages_index_end = incomingMessages_index_start + DataGeneratorPassiveActive.topWordsN;
	
	public int threshold = 0;
	public int groupsSize = 1000;
	public int pagesSize = 1000;

	protected static NumberFormat _nf = NumberFormat.getInstance();
	static {
		_nf.setMaximumIntegerDigits(100);
		_nf.setGroupingUsed(false);
	}

	public ArffData() {	}

	public ArffData(ArffData d) {
		_filename = d._filename;
		_relation = d._relation;
		_attr = d._attr;
		_data = d._data;
	}

	public ArffData(String filename) {
		_filename = filename;
		readArffFile();
	}
	
	public void setThreshold(int t){
		threshold = t;
	}
	
	public void setFriends(boolean f){
		friends = f;
	}	
	
	public void setInteractions(boolean i){
		interactions = i;
	}
	
	public void setDemographics(boolean d){
		demographics = d;
	}
	
	public void setGroups(boolean g, int s){
		groups = g;
		groupsSize = s;
	}
	
	public void setPages(boolean p, int s){
		pages = p;
		pagesSize = s;
	}
	
	public void setTraits(boolean t){
		traits = t;
	}
	
	public void setOutgoingMessages(boolean o, int s){
		outgoingMessages = o;
		outgoingMessagesSize = s;
	}
	
	public void setIncomingMessages(boolean i, int s){
		incomingMessages = i;
		incomingMessagesSize = s;
	}
	
	public void setThresholds(int t){
		threshold = t;
	}
	
	public void setFileName(String s){
		_filename = s;
		readArffFile();
	}

	public static String[] SplitRespectQuotes(String line, String split_chars) {
		ArrayList<String> ret = new ArrayList<String>();
		boolean inside_quotes = false;
		StringBuffer cur_entry = new StringBuffer();
		for (int index = 0; index < line.length(); index++) {
			char c = line.charAt(index);
			if (!inside_quotes && split_chars.indexOf(c) >= 0 && cur_entry.length() > 0) {
				ret.add(cur_entry.toString());
				cur_entry = new StringBuffer();;
			} else if (c == '\"' || c == '\'') {
				inside_quotes = !inside_quotes;
			} else
				cur_entry.append(c);
		}
		if (cur_entry.length() > 0)
			ret.add(cur_entry.toString());

		String[] a_ret = new String[ret.size()];
		for (int index = 0; index < ret.size(); index++)
			a_ret[index] = ret.get(index);

		//System.out.println("in: " + line);
		//System.out.println("out: " + ret);

		return a_ret;
	}
	
	public void readArffFile() {
		String line = null;
		int line_index = 0;
		int groupsAdded = 0;
		int pagesAdded = 0;
		int outgoingMessagesAdded = 0;
		int incomingMessagesAdded = 0;
		try {
			BufferedReader br = new BufferedReader(new FileReader(_filename));

			boolean data_started = false;
			while ((line = br.readLine()) != null) {
				//System.out.println(line);
				line_index++;

				if (line.startsWith("%") || line.trim().length() == 0)
					continue;

				if (data_started) {
					if (line.indexOf('{') >= 0)
						addSparseDataEntry(StripBraces(line));
					else{
						//System.out.println(line);
						addDataEntry(line);
					}
					continue;
				}

				// DEBUG
				//for (String s : line.split(WHITESPACE))
				//	System.out.println("\"" + s + "\"");				
				if (line.startsWith("@RELATION") || 
						line.startsWith("@Relation") ||
						line.startsWith("@relation"))
					_relation = StripQuotes(line.split(WHITESPACE)[1]);
				else if (line.startsWith("@ATTRIBUTE") ||
						line.startsWith("@Attribute") ||
						line.startsWith("@attribute")) {
					//if (line.contains("group"))
						//System.out.println(line_index + ":" + groups_index_start + "-" + line);
					if (!friends && line_index > friends_index_start && line_index <= friends_index_end){
						// nothing
					} else if (!interactions && line_index > interactions_index_start && line_index <= interactions_index_end) {
						// nothing
					} else if (!demographics && line_index > demographics_index_start && line_index <= demographics_index_end){
						// nothing
					} else if (!groups && line_index > groups_index_start && line_index <= groups_index_end){
						// nothing
					} else if (!pages && line_index > pages_index_start && line_index <= pages_index_end){
						// nothing
					} else if (!traits && line_index > traits_index_start && line_index <= traits_index_end){
						// nothing
					} else if (!outgoingMessages && line_index > outgoingMessages_index_start && line_index <= outgoingMessages_index_end){
						// nothing
					} else if (!incomingMessages && line_index > incomingMessages_index_start && line_index <= incomingMessages_index_end){
						// nothing
					} else {
						//
						if (groups && line_index > groups_index_start && line_index <= groups_index_end){
							if (groupsAdded < groupsSize){								
								//System.out.println(groupsAdded + ":" + line);
								addAttribute(line);
								groupsAdded++;
							}							
						} else if (pages && line_index > pages_index_start && line_index <= pages_index_end){
							if (pagesAdded < pagesSize){								
								//System.out.println(pagesAdded + ":" + line);
								addAttribute(line);
								pagesAdded++;
							}							
						} else if (outgoingMessages && line_index > outgoingMessages_index_start && line_index <= outgoingMessages_index_end){
							if (outgoingMessagesAdded < outgoingMessagesSize){
								//System.out.println(messagesAdded + ":" + line);
								addAttribute(line);
								outgoingMessagesAdded++;
							}			
						} else if (incomingMessages && line_index > incomingMessages_index_start && line_index <= incomingMessages_index_end){
							if (incomingMessagesAdded < incomingMessagesSize){
								//System.out.println(messagesAdded + ":" + line);
								addAttribute(line);
								incomingMessagesAdded++;
							}			
						}
						else {
							//System.out.println(line_index + " " + _attr.size() + " " + line);
							addAttribute(line);
						}
					}
					//System.out.println(line_index + ":" + line);
				}
				else if (line.startsWith("@DATA") ||
						line.startsWith("@Data") ||
						line.startsWith("@data"))
					data_started = true;
				//else
				// DEBUG
				//System.out.println("Ignoring line #" + 
				//		line_index + ": '" + line + "'");
			}	
			br.close();
		} catch (Exception e) {
			System.out.println("Error in readArffFile:");
			System.out.println("@ line [" + line_index + "]: '" + line + "'");
			e.printStackTrace();
			System.exit(1);
		}
	}

	public Attribute addAttribute(String line) {
		String[] split = SplitRespectQuotes(line, WHITESPACE_RESPECT_QUOTES);//line.split(WHITESPACE);
		int type = TYPE_UNKNOWN;
		if (split[2].equalsIgnoreCase("real"))
			type = TYPE_DOUBLE;
		else if (split[2].equalsIgnoreCase("numeric"))
			type = TYPE_DOUBLE;
		else if (split[2].equalsIgnoreCase("integer"))
			type = TYPE_INT;
		else if (split[2].indexOf("{") >= 0)
			type = TYPE_CLASS;

		Attribute a = addAttribute(split[1], type);

		// Add in class attributes if needed
		if (type == TYPE_CLASS) 
			for (int i = 2; i < split.length; i++) {
				// DEBUG
				//System.out.println("Adding attribute: \"" + split[i] + "\"");
				a.addClassVal(split[i]);
			}

		//if (type == TYPE_CLASS) 
		//	a.addClassVal("?");

		return a;
	}

	public Attribute addAttribute(String name, int type) {
		Attribute a = new Attribute(StripQuotes(name), type, _attr.size());
		_attr.add(a);
		_attrMap.put(name, a);
		return a;
	}

	public int getAttributeID(String name) {
		Attribute a = _attrMap.get(name);
		if (a != null)
			return a.my_index;
		else 
			return -1;
	}

	public static final String YES = "'y'".intern();
	public DataEntry addDataEntry(String line) {
		String split[] = line.split("[,]");

		int count = 0;
		for (int i = 3 /* offset*/; i < split.length; i++){
			if (split[i].equals(YES)){
				count++;
			}
		}

		//System.out.println(count + "-" + threshold + "-" + (threshold <= count));
		if (!(threshold <= count)){
			return null;
		}

		DataEntry d = new DataEntry(_attr.size());
		int groupSeen = 0;
		int pagesSeen = 0;
		int outgoingMessageSeen = 0;
		int incomingMessageSeen = 0;
		for (int i = 0; i < split.length; i++){
			int offset = 2; 			
			if (!friends && i > (friends_index_start-offset) && i <= (friends_index_end-offset)){
				// nothing
			} else if (!interactions && i > (interactions_index_start-offset) && i <= (interactions_index_end-offset)) {
				// nothing
			} else if (!demographics && i > (demographics_index_start-offset) && i <= (demographics_index_end-offset)){
				// nothing
			} else if (!groups && i > (groups_index_start-offset) && i <= (groups_index_end-offset)){
				// nothing
			} else if (!pages && i > (pages_index_start-offset) && i <= (pages_index_end-offset)){
				// nothing
			} else if (!traits && i > (traits_index_start-offset) && i <= (traits_index_end-offset)){
				// nothing
			} else if (!outgoingMessages && i > (outgoingMessages_index_start-offset) && i <= (outgoingMessages_index_end-offset)){
				// nothing
			} else if (!incomingMessages && i > (incomingMessages_index_start-offset) && i <= (incomingMessages_index_end-offset)){
				// nothing
			} else {				
				//System.out.println(i + ":" + _attr.get(i) + ":" + StripQuotes(split[i]) + ":" + split.length + ":" + _attr.size());
				if (groups && i > (groups_index_start-offset) && i <= (groups_index_end-offset)){						
					if (groupSeen < groupsSize){
						//System.out.println(groups + " " + i + ":" + groupSeen + ":" + _attr.size() + ":" +  ":" + StripQuotes(split[i]) + ":" + split.length + ":" + _attr.size());
						d.addData(StripQuotes(split[i]));
					}
					groupSeen++;	
				} else if (pages && i > (pages_index_start-offset) && i <= (pages_index_end-offset)){					
					if (pagesSeen < pagesSize){
						//System.out.println(groupSeen);
						//System.out.println(i + ":" + _attr.get(i) + ":" + StripQuotes(split[i]) + ":" + split.length + ":" + _attr.size());
						d.addData(StripQuotes(split[i]));
					}
					pagesSeen++;	
				} else if (outgoingMessages && i > (outgoingMessages_index_start-offset) && i <= (outgoingMessages_index_end-offset)){					
					if (outgoingMessageSeen < outgoingMessagesSize){
						//System.out.println(groupSeen);
						//System.out.println(i + ":" + _attr.get(i) + ":" + StripQuotes(split[i]) + ":" + split.length + ":" + _attr.size());
						d.addData(StripQuotes(split[i]));
					}
					outgoingMessageSeen++;	
				} else if (incomingMessages && i > (incomingMessages_index_start-offset) && i <= (incomingMessages_index_end-offset)){					
					if (incomingMessageSeen < incomingMessagesSize){
						//System.out.println(groupSeen);
						//System.out.println(i + ":" + _attr.get(i) + ":" + StripQuotes(split[i]) + ":" + split.length + ":" + _attr.size());
						d.addData(StripQuotes(split[i]));
					}
					incomingMessageSeen++;	
				} else {
					//System.out.println(i + ":" + StripQuotes(split[i]) + ":" + split.length + ":" + _attr.size() + " " + _attr.get(i));					
					d.addData(StripQuotes(split[i]));
				}
			}
		}
		if (d._entries.size() == _attr.size())
			_data.add(d);
		else {
			System.out.println(d._entries.size() + ":" + _attr.size());
			System.out.println("ERROR -- Data Entry did not have full attribute set:\n" + line);
			System.exit(1);
		}
		return d;
	}

	public DataEntry getDefaultDataEntry() {
		DataEntry de = new DataEntry(_attr.size());
		de.initializeDefaults();
		return de;
	}

	public DataEntry addSparseDataEntry(String line) {

		//System.out.println("Parsing SPARSE data entry: " + line);

		String split[] = line.split("[,]");
		DataEntry d = new DataEntry(_attr.size());

		// Initialize defaults
		d.initializeDefaults();

		// Set sparse entries
		for (int i = 0; i < split.length; i++) {
			String entry = split[i].trim();
			int first_char = entry.indexOf(' ');
			String index = entry.substring(0, first_char).trim();
			String value = StripQuotes(entry.substring(first_char).trim());
			//System.out.println(index + " : " + value);
			d.setData(new Integer(index).intValue(), value);
		}

		_data.add(d);
		//System.out.println(d);

		return d;
	}

	public class Attribute {

		public String name = null;
		public int type = TYPE_UNKNOWN;
		public int max_val = 0;
		public int my_index = 0;
		public ArrayList class_vals = null; // Only used if a class
		public HashMap   class_id_map = null; // Only used if a class

		public Attribute(String a_name, int a_type, int a_index) {
			name = a_name;
			type = a_type;
			my_index = a_index;
			if (a_type == TYPE_CLASS) {
				class_vals = new ArrayList();
				class_id_map = new HashMap();
				max_val = 0;
			}
		}

		public int addClassVal(String val) {
			val = StripQuotes(val);
			if (val.length() == 0) return -1;
			class_vals.add(val);
			class_id_map.put(val, new Integer(max_val));
			return max_val++;
		}

		public String getClassName(int id) {
			return (String)class_vals.get(id);
		}

		public Integer getClassId(String name) {
			return (Integer)class_id_map.get(name);
		}

		public String toString() {
			StringBuffer sb = new StringBuffer();
			sb.append(name + " : ");
			switch (type) {
			case TYPE_CLASS:  sb.append("CLASS " + class_vals); break;
			case TYPE_INT:    sb.append("INTEGER"); break;
			case TYPE_DOUBLE: sb.append("REAL"); break;
			default: sb.append("unknown"); break;
			}
			return sb.toString();
		}

		public String toFileString() {
			StringBuffer sb = new StringBuffer();
			sb.append("@attribute '" + name + "' ");
			switch (type) {
			case TYPE_CLASS:  sb.append("{ "); break;
			case TYPE_INT:    sb.append("integer"); break;
			case TYPE_DOUBLE: sb.append("numeric"); break;
			default: sb.append("unknown"); break;
			}

			if (type == TYPE_CLASS) {
				for (int i = 0; i < class_vals.size(); i++) {
					sb.append((i > 0 ? ", " : "") + "'" + class_vals.get(i) + "'");
				}
				sb.append(" }");
			}

			return sb.toString();
		}
	}

	public class DataEntry {
		// These are either Integers or Doubles
		ArrayList _entries = null;

		public DataEntry() {
			this(_attr.size());
		}

		public DataEntry(int initial_size) {
			_entries = new ArrayList(initial_size);
		}

		public void initializeDefaults() {
			// Initialize defaults
			for (int i = 0; i < _attr.size(); i++) {
				Attribute a = _attr.get(i);
				if (a.type == TYPE_CLASS || a.type == TYPE_INT)
					_entries.add(new Integer(0));
				else 
					_entries.add(new Double(0d));			
			}
		}

		public int addData(String entry) {
			int index = _entries.size();
			try {
				if (_attr.get(index).type == TYPE_DOUBLE) 
					_entries.add(new Double(entry));
				else if (_attr.get(index).type == TYPE_INT) 
					_entries.add(new Integer(entry));
				else if (_attr.get(index).type == TYPE_CLASS) 
					_entries.add(_attr.get(index).getClassId(entry));
				else
					throw new Exception("Unrecognized attribute type: " + _attr.get(index));
			} catch (Exception e) {
				System.out.println("Error in DataEntry.addData");
				System.out.println("@ entry: '" + entry + "'");
				e.printStackTrace();
				System.exit(1);
			}
			return index;
		}

		public void setData(int index, String entry) {
			try {
				if (_attr.get(index).type == TYPE_DOUBLE) 
					_entries.set(index, new Double(entry));
				else if (_attr.get(index).type == TYPE_INT) 
					_entries.set(index, new Integer(entry));
				else if (_attr.get(index).type == TYPE_CLASS) {
					Integer class_id = _attr.get(index).getClassId(entry);
					if (class_id == null) {
						System.out.println("setData: attibute " + index + " -> class name " + entry);
						System.out.println("class_id for class name not found: " + class_id);
						System.out.println("the options were: " + _attr.get(index).class_vals);
						try { throw new Exception(); } catch (Exception e) {
							e.printStackTrace();
						}
						System.exit(1);
					}
					else
						_entries.set(index, class_id);
				} else
					throw new Exception("Unrecognized attribute type: " + _attr.get(index));
			} catch (Exception e) {
				System.out.println("Error in DataEntry.addData");
				System.out.println("@ entry: '" + entry + "'");
				e.printStackTrace();
				System.exit(1);
			}
		}

		public Object getData(int index) {

			//System.out.println("getData, index " + Integer.toString(index) + " entries size " + Integer.toString(_entries.size()));

			/*for (int ind = 0; ind < _entries.size(); ind++) {
				Object o = _entries.get(index);
				if (ind > 0)
						System.out.println(o.toString());
				if (ind == index)
						System.out.println("object belonged to index " + Integer.toString(index));

			}*/

			return _entries.get(index);
		}		

		public String toString() {
			StringBuffer sb = new StringBuffer("[");
			for (int index = 0; index < _entries.size(); index++) {
				Object o = _entries.get(index);				
				if (index > 0)
					sb.append(", ");
				if (_attr.get(index).type == TYPE_CLASS) {

					int id = Integer.parseInt(o.toString());
					sb.append(_attr.get(index).getClassName(id) + ":" + id);
				} else
					sb.append(o.toString());

			}
			sb.append("]");
			return sb.toString();
		}

		public String toFileString() {
			StringBuffer sb = new StringBuffer();

			for (int index = 0; index < _entries.size(); index++) {
				Object o = _entries.get(index);				
				if (index > 0)
					sb.append(",");
				if (_attr.get(index).type == TYPE_CLASS) {

					int id = Integer.parseInt(o.toString());
					sb.append("'" + _attr.get(index).getClassName(id) + "'");
				} else if (o instanceof Double)
					sb.append(_nf.format((Double)o));
				else
					sb.append(o.toString());
			}
			return sb.toString();
		}
	}

	public static String StripBraces(String in) {
		StringBuffer out = new StringBuffer();
		for (int i = 0; i < in.length(); i++) {
			char c = in.charAt(i);
			if (c != '{' && c != '}')
				out.append(c);
		}
		return out.toString().trim();
	}

	public static String StripQuotes(String in) {
		StringBuffer out = new StringBuffer();
		for (int i = 0; i < in.length(); i++) {
			char c = in.charAt(i);
			if (c != '\'' && c != '`' && c != '\"' && c != '{' && c != '}' && c != ',')
				out.append(c);
		}
		return out.toString().trim();
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("Relation: " + _relation + "\n");
		sb.append("\nAttributes:\n");
		for (int i = 0; i < _attr.size(); i++)
			sb.append("[" + i + "] " + _attr.get(i) + "\n");
		sb.append("\nData:\n");
		for (int i = 0; i < _data.size(); i++)
			sb.append("[" + i + "] " + _data.get(i) + "\n");
		return sb.toString();
	}

	public String toFileString() {
		StringBuffer sb = new StringBuffer();
		sb.append("@relation " + _relation + "\n");
		for (int i = 0; i < _attr.size(); i++)
			sb.append(_attr.get(i).toFileString() + "\n");
		sb.append("@data\n");
		for (int i = 0; i < _data.size(); i++)
			sb.append(_data.get(i).toFileString() + "\n");
		return sb.toString();
	}

	public boolean writeFile(String filename) {
		try {
			FileWriter f = new FileWriter(filename);
			f.write(toFileString());
			f.close();
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	public static class SplitData {
		public ArffData _train;
		public ArffData _test;
	}

	public SplitData splitData(double train_percent) {

		SplitData s = new SplitData();
		s._train = new ArffData(this);
		s._test  = new ArffData(this);

		int[] perm = Permutation.permute(_data.size());
		s._train._data = new ArrayList<DataEntry>();
		s._test._data  = new ArrayList<DataEntry>();

		int split_point = (int)Math.round(train_percent * _data.size());
		for (int i = 0; i < _data.size(); i++) {
			if (i <= split_point)
				s._train._data.add(_data.get(perm[i]));
			else
				s._test._data.add(_data.get(perm[i]));
		}

		return s;
	}

	public static class FoldData {
		public FoldData(int folds) {
			_train = new ArffData[folds];
			_test  = new ArffData[folds];
		}
		public void writeData() {
			for (int f = 0; f < _train.length; f++) {
				_train[f].writeFile(_train[f]._filename);
				_test[f]. writeFile( _test[f]._filename);
			}
		}
		public ArffData[] _train;
		public ArffData[] _test;
	}

	public FoldData foldData(int folds) {

		FoldData s = new FoldData(folds);
		for (int f = 0; f < folds; f++) {
			s._train[f] = new ArffData(this);
			s._train[f]._filename += ".train." + (f+1);
			s._train[f]._data = new ArrayList<DataEntry>();
			s._test[f] = new ArffData(this);
			s._test[f]._filename += ".test." + (f+1);
			s._test[f]._data = new ArrayList<DataEntry>();
		}

		int[] perm = Permutation.permute(_data.size());

		double fold_percent = 1d/(double)folds;
		for (int f = 0; f < folds; f++) {

			int test_min = (f == 0 ? 0 : 
				(int)Math.round((double)f * fold_percent * _data.size()));
			int test_max = (f == folds - 1 ? _data.size() : 
				(int)Math.round((double)(f+1) * fold_percent * _data.size()));

			for (int i = 0; i < _data.size(); i++) {
				if (i < test_min || i > test_max)
					s._train[f]._data.add(_data.get(perm[i]));
				else
					s._test[f]._data.add(_data.get(perm[i]));
			}
		}

		return s;
	}	

	public static void main(String args[]) {
		System.out.println("Running ArffData.main:\n");

		/*		for (int i = 0; i < 10; i++){

			String trainName = "active.arff" + ".train." + (i+1);
			String testName  = "active.arff" + ".test."  + (i+1);

			System.out.println("---");
			ArffData _trainData = new ArffData(trainName, 0, demographics, traits, groups, conversations);
			System.out.println(trainName + ":" + _trainData._data.size());
			System.out.println("---");
			ArffData _testData  = new ArffData(testName, 1, demographics, traits, groups, conversations);
			System.out.println(testName + ":" + _testData._data.size());

		}*/
//		public ArffData(String filename, int _threshold, int _groupsSize, int _pagesSize, int _messagesSize, boolean _demographics, boolean _groups, boolean _pages, boolean _traits, boolean _conversations) {
		
		ArffData f1 = new ArffData();
		f1.setFriends(false);
		f1.setInteractions(false);
		f1.setDemographics(false);
		f1.setGroups(false, 10);
		f1.setPages(false, 10);
		f1.setTraits(false);
		f1.setOutgoingMessages(false, 10);
		f1.setIncomingMessages(true, 10);
		f1.setFileName("active_all_1000.arff");
		for (Attribute s : f1._attr){
			System.out.println(s);
		}
		System.out.println(f1._attr.size());


		//		System.out.println(f1._attr);

		//ArffData f1 = new ArffData("active.arff.train.2",false,false,false);
		//System.out.println(f1._data.size());

		/*for (int i = 0; i <= 5; i++){
			ArffData f1 = new ArffData("active.arff",i,false,false,false);
			System.out.println("Threshold " + i + " data size " + f1._data.size());
		}*/

		//System.out.println(f1);

		//SplitData s = f1.splitData(.8);
		//System.out.println("Writing training file: " + s._train.writeFile(filename + "_train.arff"));
		//System.out.println("Writing testing  file: " + s._test.writeFile (filename + "_test.arff"));

		//FoldData f = f1.foldData(10);
		//f.writeData();		
		System.out.println("Finished ArffData.main.");
	}
}
