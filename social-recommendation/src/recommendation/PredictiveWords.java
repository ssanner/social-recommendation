package recommendation;

import java.sql.SQLException;

import org.nicta.lr.util.UserUtil;

public class PredictiveWords {

	public static void main(String[] args) throws SQLException {
		System.out.println("test");
		for (Long s : UserUtil.getAppUserIds()){
			System.out.println(s);
		}
	}
	
}
