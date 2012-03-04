package recommendation;

import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

import org.nicta.lr.util.EDirectionType;
import org.nicta.lr.util.EInteractionType;
import org.nicta.lr.util.Interaction;
import org.nicta.lr.util.UserUtil;

public class PredictiveWords {

	public static void main(String[] args) throws SQLException {
		PredictiveWords p = new PredictiveWords();
		p.getAllComments(EInteractionType.ALL_COMMENTS, EDirectionType.OUTGOING);
	}
	
	
	public static Set<Long> APP_USERS;
	public static Set<Long> ALL_USERS;
	public static Map<Long,String> UID_2_NAME;
	
	
	public void getAllComments(EInteractionType type, EDirectionType dir) throws SQLException{
		
			try {
			APP_USERS = UserUtil.getAppUserIds();
			ALL_USERS = UserUtil.getUserIds();
			UID_2_NAME = UserUtil.getUserNames();
			} catch (SQLException e) {
				System.out.println(e);
				System.exit(1);
			}
		
			System.out.println("=========================");
			Interaction i = UserUtil.getUserInteractions(EInteractionType.ALL_COMMENTS, EDirectionType.OUTGOING);
			for (long uid : APP_USERS) {
				String uid_name = UID_2_NAME.get(uid);				
				Set<Long> inter = i.getInteractions(uid);
				System.out.println(uid + ", " + uid_name + " -- " + type + ": " + (inter == null ? 0 : inter.size()));
				System.out.print(" * [ ");
				boolean first = true;
				if (inter != null) 
					for (Long uid2 : inter) {
						String uid2_name = UID_2_NAME.get(uid2);
						System.out.print((first ? "" : ", ") + uid2_name);
						first = false;
					}
				System.out.println(" ]");
			}
			System.out.println("=========================");		
		
		
		
		
	}
	
}
