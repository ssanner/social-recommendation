package recommendation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Set;

import org.nicta.lr.util.EDirectionType;
import org.nicta.lr.util.EInteractionType;
import org.nicta.lr.util.Interaction;
import org.nicta.lr.util.SQLUtil;
import org.nicta.lr.util.UserUtil;

public class PredictiveWords {

	public static void main(String[] args) throws SQLException {
		
		Statement statement = SQLUtil.getStatement();
		
		String userQuery = "SELECT uid FROM linkrlikes";
		
		ResultSet result = statement.executeQuery(userQuery);
		while (result.next()) {
			System.out.println(result.getLong("uid"));
		}
		statement.close();
		
		//PredictiveWords p = new PredictiveWords();
		//p.getAllComments(EInteractionType.ALL_COMMENTS, EDirectionType.OUTGOING);
	}


	public void getAllComments(EInteractionType type, EDirectionType dir) throws SQLException{		
		Interaction i = UserUtil.getUserInteractions(EInteractionType.ALL_COMMENTS, EDirectionType.OUTGOING);
		for (long uid : ExtractRelTables.APP_USERS) {
			String uid_name = ExtractRelTables.UID_2_NAME.get(uid);				
			Set<Long> inter = i.getInteractions(uid);
			System.out.println(uid + ", " + uid_name + " -- " + type + ": " + (inter == null ? 0 : inter.size()));
			System.out.print(" * [ ");
			boolean first = true;
			if (inter != null) 
				for (Long uid2 : inter) {
					String uid2_name = ExtractRelTables.UID_2_NAME.get(uid2);
					System.out.print((first ? "" : ", ") + uid2_name);
					first = false;
				}
			System.out.println(" ]");
		}		
	}

}
