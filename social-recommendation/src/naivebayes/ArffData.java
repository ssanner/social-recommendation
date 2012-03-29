/** Loads an ARFF file, either sparse or dense format.
 *   
 * @author Scott Sanner (ssanner@gmail.com)
 */

package naivebayes;

import java.io.*;
import java.util.*;

import javax.xml.stream.events.Attribute;

import util.*;

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
		try {
			BufferedReader br = new BufferedReader(new FileReader(_filename));
			
			boolean data_started = false;
			while ((line = br.readLine()) != null) {
				
				line_index++;
				
				if (line.startsWith("%") || line.trim().length() == 0)
					continue;
				
				if (data_started) {
					if (line.indexOf('{') >= 0)
						addSparseDataEntry(StripBraces(line));
					else
						addDataEntry(line);
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
						 line.startsWith("@attribute"))
					addAttribute(line);
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
				System.out.println("Adding attribute: \"" + split[i] + "\"");
				a.addClassVal(split[i]);
			}
		
		if (type == TYPE_CLASS) 
			a.addClassVal("?");
		
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
	
	public DataEntry addDataEntry(String line) {
		String split[] = line.split("[,]");
		DataEntry d = new DataEntry(_attr.size());
		for (String s : split) 
			d.addData(StripQuotes(s));
		if (d._entries.size() == _attr.size())
			_data.add(d);
		else {
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
			sb.append("@attribute\t" + name + "\t");
			switch (type) {
			case TYPE_CLASS:  sb.append(" { "); break;
			case TYPE_INT:    sb.append("integer"); break;
			case TYPE_DOUBLE: sb.append("numeric"); break;
			default: sb.append("unknown"); break;
			}
			
			if (type == TYPE_CLASS) {
				for (int i = 0; i < class_vals.size() - 1; i++)
					sb.append("'" + class_vals.get(i) + "'\t");
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
					System.out.println(o);
					int id = Integer.parseInt(o.toString());
					sb.append(_attr.get(index).getClassName(id) + ":" + id);
				} else
					sb.append(o.toString());
					
			}
			sb.append("]");
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
	
	public static void main(String args[]) {
		System.out.println("Running ArffData.main:\n");
		
		ArffData f1 = new ArffData("data.arff");
		System.out.println(f1);
		
	}
}
