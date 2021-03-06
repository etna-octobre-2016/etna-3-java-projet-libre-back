package com.web.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.helper.CommonHelper;
import com.web.model.Category;
import com.web.model.Liste;
import com.web.model.Session;
import com.web.model.Task;
import com.web.model.User;

public class DbManager {
	
	//Objects spécifiques aux differents appels vers les bases (locales ou distantes).	
	public Connection			myConnect;	
	public Statement			myStatement;
	public ResultSet			myResultSet;

	//Objects de Meta-Information sur la Database connectée, et sur la requête effectuée.
	public DatabaseMetaData		myDbMetaData;
	public ResultSetMetaData	myResultSetMetaData;
	
	public PreparedStatement 	myPreparedStatement;
	
	public String 				arrayContent[][];
	public String 				arrayHeader[];
	public String 				strConnectURL;
	public static DbManager			dbmanager;
	
	private static final String DB_DRIVER = "com.mysql.jdbc.Driver";
	private static final String DB_CONNECTION = "jdbc:mysql://localhost:3306/projetlibre";
	private static final String DB_USER = "root";
	private static final String DB_PASSWORD = "";
	
	public static synchronized DbManager getInstance( ) {
		 if (dbmanager == null)
			 dbmanager = new DbManager();
		 return dbmanager;
	}
	
	public static Connection getDBConnection() {
		Connection dbConnection = null;
		try {
			//1ere etape: Chargement de la classe de driver, responsable - par contrat d'interfaces - de la connection vers le SGBD
			//Il existe 4 types de driver (I, II, III, IV): 2 locaux, et 2 remote. More infos: http://java.sun.com/jdbc/drivers.html
 			//Class.forName("sun.jdbc.odbc.JdbcOdbcDriver");
			Class.forName(DB_DRIVER);
		} catch (ClassNotFoundException e) { System.out.println(e.getMessage()); }

		try {
			dbConnection = DriverManager.getConnection(DB_CONNECTION, DB_USER,DB_PASSWORD);
			return dbConnection;
		} catch (SQLException e) {
			System.out.println(e.getMessage());
		}

		return dbConnection;

	}
	
	public void closer(){
		if (myPreparedStatement != null) { 
			try { myPreparedStatement.close(); } 
			catch (SQLException e) { e.printStackTrace();} 
		}
		if (myConnect != null) { 
			try { myConnect.close(); } 
			catch (SQLException e) { e.printStackTrace();} 
		}	
	}
	/***********************************
	 ************* USER ************
	 ***********************************/
	
	public User getUser(String id) throws SQLException {
		String selectSQL = "SELECT * FROM user WHERE email LIKE ?";
		User user = new User();
		try {
			
			myConnect 			= getDBConnection();
			myPreparedStatement = myConnect.prepareStatement(selectSQL);
			myPreparedStatement.setString(1, id);

			// execute select SQL stetement
			ResultSet rs = myPreparedStatement.executeQuery();
			
			if (rs.first()){
				
				//rs.next();	
			
				int userid = rs.getInt("iduser");
				String email = rs.getString("email");
				String username = rs.getString("username");
				String mdp = rs.getString("mdp");
				
				// je modifie l'objet a retourner : User
				user.setId(userid);
				user.setEmail(email);
				user.setUserName(username);
				user.setPassword(mdp);
				//user.setCategorieList(getAllUserCategories(userid).toString());
				
			}
		} 
		catch (SQLException e) { System.out.println(e.getMessage());} 
		finally { 	
			if (myPreparedStatement != null) { myPreparedStatement.close(); }
			if (myConnect != null) { myConnect.close(); }	
		}
		
		return user;
	}
	
	public boolean InsertUser(User user) {
		String sql = "INSERT INTO user VALUES (NULL,?,?,?)";
		String email = user.getEmail();
		if (userExist(email)){
			System.out.println(" This id (email) "+email+" is already inserted in db");
			return false;
		}
		
		try {
			myConnect 			= getDBConnection();
			myPreparedStatement = myConnect.prepareStatement(sql);
			
			myPreparedStatement.setString(1, user.getUserName());
			myPreparedStatement.setString(2, email);
			myPreparedStatement.setString(3,CommonHelper.toSha1(user.getPassword()));
			
			int rowsInserted = myPreparedStatement.executeUpdate();
			if (rowsInserted > 0) {
			    System.out.println("A new user has been inserted successfully!");
			    return true;
			}
		}
		catch (SQLException e) { System.out.println(e.getMessage());}
		finally { closer(); }
		
		System.out.println("user NOT Inserted !");
		return false;
	}
	

	
	public boolean userExist(String email){
		String selectSQL = "SELECT * FROM user WHERE email LIKE ?";
		try {
			myConnect 			= getDBConnection();
			myPreparedStatement = myConnect.prepareStatement(selectSQL);
			myPreparedStatement.setString(1, email);
			ResultSet rs = myPreparedStatement.executeQuery();
			
			if (rs.first())
				return true;
		} 
		catch (SQLException e) { e.printStackTrace(); }
		finally { closer(); }
		
		return false;
	}
	
	/************************************
	 ************* CATEGORY *************
	 ***********************************/
	
	public JSONArray getAllUserCategories(int id) {
		String sql = "SELECT * FROM category WHERE user_iduser = ?";
		JSONArray list = new JSONArray();
		int userId = (Session.getInstance().getUser()!=null && Session.getInstance().getUser().getId()>0)? Session.getInstance().getUser().getId():id;
		try {
			myConnect 			= getDBConnection();
			myPreparedStatement = myConnect.prepareStatement(sql);
			myPreparedStatement.setInt(1, userId);
			
			myResultSet = myPreparedStatement.executeQuery();
			
			while (myResultSet.next()) {
				JSONObject obj = new JSONObject();
				try {
					obj.put("idcategory", myResultSet.getString("idcategory"));
					obj.put("name", myResultSet.getString("name"));
				} catch (JSONException e) { e.printStackTrace(); }
				list.put(obj);
			}
		} catch (SQLException e) { e.printStackTrace(); }
		finally { closer(); }
		System.out.println(" list cat "+list);
		return list;
	}
	
	public JSONArray getCategory( String catName ) {
		String sql = "SELECT category.idcategory, category.name FROM category WHERE user_iduser = ? AND category.name like ?";
		JSONArray list = new JSONArray();
		int userId = Session.getInstance().getUser().getId();
		try {
			myConnect 			= getDBConnection();
			myPreparedStatement = myConnect.prepareStatement(sql);
			myPreparedStatement.setInt(1, userId);
			myPreparedStatement.setString(2, catName);
			
			myResultSet = myPreparedStatement.executeQuery();
			
			if (myResultSet.first()) {
				JSONObject obj = new JSONObject();
				try {
					obj.put("idcategory", myResultSet.getString("idcategory"));
					obj.put("name", myResultSet.getString("name"));
				} catch (JSONException e) { e.printStackTrace(); }
				list.put(obj);
			}
		} catch (SQLException e) { e.printStackTrace(); }
		finally { closer(); }
		System.out.println(" list cat "+list);
		return list;
	}
	
	public JSONArray sqlGetCategory( int id ) {
		String sql = "SELECT category.idcategory, category.name FROM category WHERE user_iduser = ? AND category.idcategory = ?";
		JSONArray list = new JSONArray();
		int userId = Session.getInstance().getUser().getId();
		try {
			myConnect 			= getDBConnection();
			myPreparedStatement = myConnect.prepareStatement(sql);
			myPreparedStatement.setInt(1, userId);
			myPreparedStatement.setInt(2, id);
			
			myResultSet = myPreparedStatement.executeQuery();
			
			if (myResultSet.first()) {
				JSONObject obj = new JSONObject();
				try {
					obj.put("idcategory", myResultSet.getString("idcategory"));
					obj.put("name", myResultSet.getString("name"));
				} catch (JSONException e) { e.printStackTrace(); }
				list.put(obj);
			}
		} catch (SQLException e) { e.printStackTrace(); }
		finally { closer(); }
		System.out.println(" list cat "+list);
		return list;
	}
	
	public JSONArray sqlInsertCategory(Category cat) {
		String sql = "INSERT INTO category VALUES (NULL,?,?)";
		int userId = Session.getInstance().getUser().getId();
		try {
			myConnect 			= getDBConnection();
			myPreparedStatement = myConnect.prepareStatement(sql);
			
			myPreparedStatement.setString(1, cat.getName());
			myPreparedStatement.setInt(2, userId);
			System.out.println(" cat "+cat.getUser_iduser());
			int rowsInserted = myPreparedStatement.executeUpdate();
			if (rowsInserted > 0) {
			    System.out.println("A new category has been inserted successfully!");
			    return getCategory(cat.getName());
			}
		}
		catch (SQLException e) { System.out.println(e.getMessage());}
		finally { closer(); }
		
		System.out.println("category has not been Inserted !");
		return null;
	}
	
	public JSONArray sqlUpdateCategory(Category cat) {
		String sql = "UPDATE category SET name = ? WHERE idcategory = ? AND user_iduser = ?";
		int userId = Session.getInstance().getUser().getId();
		try {
			myConnect 			= getDBConnection();
			myPreparedStatement = myConnect.prepareStatement(sql);
			
			myPreparedStatement.setString(1, cat.getName());
			myPreparedStatement.setInt(2, cat.getIdcategory());
			myPreparedStatement.setInt(3, userId);
			
			int rowsInserted = myPreparedStatement.executeUpdate();
			if (rowsInserted > 0) {
			    System.out.println("A new category has been updated successfully!");
			    return getCategory(cat.getName());
			}
		}
		catch (SQLException e) { System.out.println(e.getMessage());}
		finally { closer(); }
		
		System.out.println("category has not been Update !");
		return null;
	}
	
	public JSONArray sqlDeleteCategory(Category cat) {
		String sql = "DELETE FROM category WHERE idcategory = ? AND user_iduser = ?";
		int userId = Session.getInstance().getUser().getId();
		String nameCat = cat.getName();
		try {
			myConnect 			= getDBConnection();
			myPreparedStatement = myConnect.prepareStatement(sql);
			
			myPreparedStatement.setInt(1, cat.getIdcategory());
			myPreparedStatement.setInt(2, userId);
			
			int rowsInserted = myPreparedStatement.executeUpdate();
			if (rowsInserted > 0) {
				JSONArray list = new JSONArray();
				JSONObject obj = new JSONObject();
				try {
					obj.put( "name",nameCat);
				} catch (JSONException e) { e.printStackTrace(); }
				
				list.put(obj);
			    System.out.println("A new category has been delete successfully!");
			    return list;
			}
		}
		catch (SQLException e) { System.out.println(e.getMessage());}
		finally { closer(); }
		System.out.println("category has not been delete !");
		return null;
	}
	
	/***********************************
	 ************* LIST ****************
	 ***********************************/
	
	public JSONArray sqlGetAllList(int id) {
		String sql = "SELECT * FROM list WHERE category_idcategory = ?";
		JSONArray resulList = new JSONArray();
		try {
			myConnect 			= getDBConnection();
			myPreparedStatement = myConnect.prepareStatement(sql);
			
			myPreparedStatement.setInt(1, id);
			
			myResultSet = myPreparedStatement.executeQuery();
			
			while (myResultSet.next()) {
				JSONObject obj = new JSONObject();
				try {
					obj.put("idlist", myResultSet.getInt("idlist"));
					obj.put("name", myResultSet.getString("name"));
					obj.put("description", myResultSet.getString("description"));
					obj.put("idcategory", myResultSet.getInt("category_idcategory"));
				} catch (JSONException e) { e.printStackTrace(); }
				resulList.put(obj);
			}
		}
		catch (SQLException e) { System.out.println(e.getMessage());}
		finally { closer(); }
		return resulList;
	}
	
	public JSONArray sqlGetList(Liste list) {
		String sql = "SELECT * FROM list WHERE name = ? AND category_idcategory = ?";
		JSONArray resulList = new JSONArray();
		try {
			myConnect 			= getDBConnection();
			myPreparedStatement = myConnect.prepareStatement(sql);
			
			myPreparedStatement.setString(1, list.getName());
			myPreparedStatement.setInt(2, list.getCategory_idcategory());
			
			myResultSet = myPreparedStatement.executeQuery();
			
			if (myResultSet.first()) {
				JSONObject obj = new JSONObject();
				try {
					obj.put("idlist", myResultSet.getInt("idlist"));
					obj.put("name", myResultSet.getString("name"));
					//obj.put("description", myResultSet.getString("description"));
					obj.put("category_idcategory", myResultSet.getInt("category_idcategory"));
				} catch (JSONException e) { e.printStackTrace(); }
				resulList.put(obj);
			}
		}
		catch (SQLException e) { System.out.println(e.getMessage());}
		finally { closer(); }
		return resulList;
	}
	
	public JSONArray sqlInsertList(Liste list) {
		String sql = "INSERT INTO list VALUES (NULL,?,?,?)";
		JSONArray resulList = new JSONArray();
		try {
			myConnect 			= getDBConnection();
			myPreparedStatement = myConnect.prepareStatement(sql);
			
			myPreparedStatement.setString( 1, 	list.getName()					);
			myPreparedStatement.setString( 2, 	list.getDescription()			);
			myPreparedStatement.setInt(	   3,	list.getCategory_idcategory()	);
			
			int rowsInserted = myPreparedStatement.executeUpdate();
			if (rowsInserted > 0) {
			    System.out.println("A new list has been inserted successfully!");
			    return sqlGetList(list);
			}
		}
		catch (SQLException e) 
		{ System.out.println(e.getMessage());
			System.out.println("list has not been Inserted !"); 
			return resulList; }
		finally { closer(); }
		
		System.out.println("list has not been Inserted !");
		return resulList;
	}
	
	public JSONArray sqlUpdateList(Liste lst) {
		String sql = "UPDATE list SET name = ?, description = ? WHERE idlist = ? AND category_idcategory = ?";
		try {
			myConnect 			= getDBConnection();
			myPreparedStatement = myConnect.prepareStatement(sql);
			
			myPreparedStatement.setString(	1, 	lst.getName()					);
			myPreparedStatement.setString(	2, 	lst.getDescription()			);
			myPreparedStatement.setInt(		3, 	lst.getIdlist()					);
			myPreparedStatement.setInt(		4, 	lst.getCategory_idcategory()	);
			
			int rowsInserted = myPreparedStatement.executeUpdate();
			if (rowsInserted > 0) {
			    System.out.println("A new list has been updated successfully!");
			    return sqlGetList(lst);
			}
		}
		catch (SQLException e) { System.out.println(e.getMessage());}
		finally { closer(); }
		
		System.out.println("a list has not been Update !");
		return null;
	}
	
	public JSONArray sqlDeleteList(Liste lst) {
		String sql = "DELETE FROM List WHERE idlist = ? AND category_idcategory = ?";
		JSONArray resulList = new JSONArray();
		try {
			myConnect 			= getDBConnection();
			myPreparedStatement = myConnect.prepareStatement(sql);
			
			myPreparedStatement.setInt(1, lst.getIdlist());
			myPreparedStatement.setInt(2, lst.getCategory_idcategory());
			
			int rowsInserted = myPreparedStatement.executeUpdate();
			if (rowsInserted > 0) {
				JSONObject obj = new JSONObject();
				try {
					obj.put( "idlist",lst.getIdlist());
					obj.put( "name",lst.getName());
					obj.put( "idcategory",lst.getCategory_idcategory());
				} catch (JSONException e) { e.printStackTrace(); }
				
				resulList.put(obj);
			    System.out.println("A new category has been delete successfully!");
			    return resulList;
			}
		}
		catch (SQLException e) { System.out.println(e.getMessage());}
		finally { closer(); }
		
		System.out.println("category has not been delete !");
		return resulList;
	}
	
	/***********************************
	 ************* TASK  ***************
	 ***********************************/
	
	public JSONArray sqlGetAllTasks(int id) {
		String sql = "SELECT * FROM task WHERE list_idlist = ?";
		JSONArray resulList = new JSONArray();
		try {
			myConnect 			= getDBConnection();
			myPreparedStatement = myConnect.prepareStatement(sql);
			
			myPreparedStatement.setInt(1, id);
			
			myResultSet = myPreparedStatement.executeQuery();
			
			while (myResultSet.next()) {
				JSONObject obj = new JSONObject();
				try {
					obj.put("idtask", myResultSet.getInt("idtask"));
					obj.put("name", myResultSet.getString("name"));
					obj.put("description", myResultSet.getString("description"));
					obj.put("list_idlist", myResultSet.getInt("list_idlist"));
					obj.put("done", myResultSet.getInt("done"));
				} catch (JSONException e) { e.printStackTrace(); }
				resulList.put(obj);
			}
		}
		catch (SQLException e) { System.out.println(e.getMessage());}
		finally { closer(); }
		return resulList;
	}
	
	public JSONArray sqlGetTask(Task task) {
		String sql = "SELECT * FROM task WHERE name = ? AND list_idlist = ?";
		JSONArray resulList = new JSONArray();
		try {
			myConnect 			= getDBConnection();
			myPreparedStatement = myConnect.prepareStatement(sql);
			
			myPreparedStatement.setString(1, task.getName());
			myPreparedStatement.setInt(2, task.getList_idlist());
			
			myResultSet = myPreparedStatement.executeQuery();
			
			if (myResultSet.first()) {
				JSONObject obj = new JSONObject();
				try {
					obj.put("idtask", myResultSet.getInt("idtask"));
					obj.put("name", myResultSet.getString("name"));
					obj.put("description", myResultSet.getString("description"));
					obj.put("list_idlist", myResultSet.getInt("list_idlist"));
					obj.put("done", myResultSet.getInt("done"));
				} catch (JSONException e) { e.printStackTrace(); }
				resulList.put(obj);
			}
		}
		catch (SQLException e) { System.out.println(e.getMessage());}
		finally { closer(); }
		return resulList;
	}
	
	public JSONArray sqlInsertTask(Task task) {
		String sql = "INSERT INTO task VALUES (NULL,?,?,?,?)";
		JSONArray resulList = new JSONArray();
		try {
			myConnect 			= getDBConnection();
			myPreparedStatement = myConnect.prepareStatement(sql);
			
			myPreparedStatement.setString( 1, 	task.getName()			);
			myPreparedStatement.setString( 2, 	task.getDescription()	);
			myPreparedStatement.setInt(	   3,	task.getList_idlist()	);
			myPreparedStatement.setInt(	   4,	task.getDone()			);
			
			int rowsInserted = myPreparedStatement.executeUpdate();
			if (rowsInserted > 0) {
			    System.out.println("A new task has been inserted successfully!");
			    return sqlGetTask(task);
			}
		}
		catch (SQLException e) 
		{ System.out.println(e.getMessage());
			System.out.println("task has not been Inserted !"); 
			return resulList; }
		finally { closer(); }
		
		System.out.println("task has not been Inserted !");
		return resulList;
	}
	
	public JSONArray sqlUpdateTask(Task task) {
		String sql = "UPDATE task SET name = ?, description = ?, done = ? WHERE idtask = ? AND list_idlist = ?";
		try {
			myConnect 			= getDBConnection();
			myPreparedStatement = myConnect.prepareStatement(sql);
			
			myPreparedStatement.setString(	1, 	task.getName()			);
			myPreparedStatement.setString(	2, 	task.getDescription()	);
			myPreparedStatement.setInt(		3, 	task.getDone()			);
			myPreparedStatement.setInt(		4, 	task.getIdtask()		);
			myPreparedStatement.setInt(		5, 	task.getList_idlist()	);
			
			int rowsInserted = myPreparedStatement.executeUpdate();
			if (rowsInserted > 0) {
			    System.out.println("A new task has been updated successfully!");
			    return sqlGetTask(task);
			}
		}
		catch (SQLException e) { System.out.println(e.getMessage());}
		finally { closer(); }
		
		System.out.println("a task has not been Update !");
		return null;
	}
	
	public JSONArray sqlDeleteTask(Task task) {
		String sql = "DELETE FROM task WHERE idtask = ? AND list_idlist = ?";
		JSONArray resulList = new JSONArray();
		try {
			myConnect 			= getDBConnection();
			myPreparedStatement = myConnect.prepareStatement(sql);
			
			myPreparedStatement.setInt(1, task.getIdtask()		);
			myPreparedStatement.setInt(2, task.getList_idlist()	);
			
			int rowsInserted = myPreparedStatement.executeUpdate();
			if (rowsInserted > 0) {
				JSONObject obj = new JSONObject();
				try {
					obj.put( "idtask",		task.getIdtask()		);
					obj.put( "name",		task.getName()			);
					obj.put( "list_idlist",	task.getList_idlist()	);
				} catch (JSONException e) { e.printStackTrace(); }
				
				resulList.put(obj);
			    System.out.println("A new task has been delete successfully!");
			    return resulList;
			}
		}
		catch (SQLException e) { System.out.println(e.getMessage());}
		finally { closer(); }
		
		System.out.println("task has not been delete !");
		return resulList;
		
	}
	
	
	/************************************
	 ************* TEST  ****************
	 ************************************
	 ************************************
	 ************************************/
	
	/**
	 * Fonction de test
	 * @param sql
	 */
	public void dbExecute(String sql)
	{
		try
		{	
 			//3eme etape: Creation de l'object de connection
			myConnect 					= getDBConnection();
		
			//Option: Acces a un jeu de meta information sur la base avec laquelle on dialogue.
			myDbMetaData 				= myConnect.getMetaData();

			System.out.println("DbManager: dbConnect: show DataBase MetaData:");
			System.out.println("DbManager: dbConnect: productName=" 	+ myDbMetaData.getDatabaseProductName());
			System.out.println("DbManager: dbConnect: productVersion=" 	+ myDbMetaData.getDatabaseProductVersion());
			//etc... de nombreuses autres info sont disponibles
			
			//4eme etape: creation d'une instruction/formule, socle pour executer des requetes
			myStatement						= myConnect.createStatement();

			//5eme etape: invocation d'une requête (soit une selection stockee dans un ResultSet, soit un update/delete/insert renvoyant le nbr de ligne modifiee(s)).
			myResultSet					= myStatement.executeQuery(sql);
			//int nbrRow				= myState.executeUpdate("Delete from personne where id=2");

			//Stockage de l'historique des requetes dans un fichier, histoire d'avoir un backup
			//IoManager.writeFile("Select * from personne;", "queryPerformer.sql", false); 

			//Option: Acces a un jeu de meta information sur la base avec laquelle on dialogue.
			myResultSetMetaData			= myResultSet.getMetaData();

			System.out.println("\r\nDbManager: dbConnect: show Query MetaData:");
			
			int nbrColumn				= myResultSetMetaData.getColumnCount();
			List<String[]>	list		= new ArrayList<String[]>();
			arrayHeader					= new String[nbrColumn];
			
			//la première colonne porte l'index 1, ET NON 0 !!!
			for (int i = 0; i != nbrColumn; i++) 
			{
				arrayHeader[i]	= myResultSetMetaData.getColumnName(i + 1);
				
				System.out.println("DbManager: dbConnect: MetaInfo: columnName=" + myResultSetMetaData.getColumnName(i + 1) + ", columnType=" + myResultSetMetaData.getColumnTypeName(i + 1));
				//etc... de nombreuses autres info sont disponibles
			}

			System.out.println("\r\nDbManager: dbConnect: show Query Data:");
			
			//6eme etape: parcours du resultSet et de ses données.
			//la première colonne porte l'index 1, ET NON 0 !!!
			while (myResultSet.next()) //incremente aussi l'index pour la lecture des données
			{
				String[] content 		= new String[nbrColumn];
				content[0]				= myResultSet.getString(1);         
				content[1]				= myResultSet.getString(2);         

				list.add(content);
				
				System.out.println("DbManager: dbConnect: resultSet 1st column=" 	+ content[0]); 
				System.out.println("DbManager: dbConnect: resultSet 2nd column=" 	+ content[1] + "\r\n"); 
				
				//l'object ResultSet peut invoker bon nombre de getters: 
				//getShort, getDouble, getInt, getByte, getboolean, getBigDecimal, getBinaryStream, getAsciiStream, 
				//getDate, getFloat, getBlob, getClob...						
			}
			
			//instanciation du String[][]
			arrayContent				= new String[list.size()][nbrColumn];
			int index					= 0;
		
			for (String[] content : list)
				arrayContent[index++] = content; 	
			
			//Exemple de prepared Statement
			myPreparedStatement 		= myConnect.prepareStatement(sql); 			
			
			//myPreparedStatement.setInt(1, 3);
			//myPreparedStatement.setString(2, 'myStrValue');
			
			//myPreparedStatement.executeUpdate();
			//myPreparedStatement.executeQuery();	
		}
		catch (SQLException e) 				{System.out.println("dbConnect SQLException: " + e.toString()); e.printStackTrace();}	
		catch (Exception e) 					{System.out.println("dbConnect Exception: " + e.toString()); 	e.printStackTrace();}	
		finally
		{
			try {myStatement.close();}
			catch (java.sql.SQLException e)	{System.out.println("dbDisconnect: close statement: " + e.toString());}
			catch (Exception e)	{System.out.println("dbDisconnect: close statement: " + e.toString());}		
			
			try {myConnect.close();}
			catch (java.sql.SQLException e)	{System.out.println("dbDisconnect: close statement: " + e.toString());}
			catch (Exception e)	{System.out.println("dbDisconnect: close connection: " + e.toString());}		
		}
	}
	
	/**
	 * fonction de test
	 * @param email
	 */
	public void executeSelect(String email){
		try {
			  myConnect 					= getDBConnection();
			  if (myConnect==null)
				  return;
			  
			  	String sql = "SELECT * FROM user WHERE email LIKE ?";
				myDbMetaData 				= myConnect.getMetaData();
				myStatement					= myConnect.createStatement();
				myResultSet					= myStatement.executeQuery(sql);
				myResultSetMetaData			= myResultSet.getMetaData();

				System.out.println("\r\nDbManager: dbConnect: show Query MetaData:");
				
				int nbrColumn				= myResultSetMetaData.getColumnCount();
				List<String[]>	list		= new ArrayList<String[]>();
				arrayHeader					= new String[nbrColumn];
	        
				//la première colonne porte l'index 1, ET NON 0 !!!
				for (int i = 0; i != nbrColumn; i++) 
				{
					arrayHeader[i]	= myResultSetMetaData.getColumnName(i + 1);
					
					System.out.println("DbManager: dbConnect: MetaInfo: columnName=" + myResultSetMetaData.getColumnName(i + 1) + ", columnType=" + myResultSetMetaData.getColumnTypeName(i + 1));
				}

				System.out.println("\r\nDbManager: dbConnect: show Query Data:");
				//la première colonne porte l'index 1, ET NON 0 !!!
				while (myResultSet.next()) //incremente aussi l'index pour la lecture des données
				{
					String[] content 		= new String[nbrColumn];
					content[0]				= myResultSet.getString(1);         
					content[1]				= myResultSet.getString(2);         

					list.add(content);
					
					System.out.println("DbManager: dbConnect: resultSet 1st column=" 	+ content[0]); 
					System.out.println("DbManager: dbConnect: resultSet 2nd column=" 	+ content[1] + "\r\n"); 
					
					//l'object ResultSet peut invoker bon nombre de getters: 
					//getShort, getDouble, getInt, getByte, getboolean, getBigDecimal, getBinaryStream, getAsciiStream, 
					//getDate, getFloat, getBlob, getClob...						
				}
		}
		catch (SQLException e) 				{System.out.println("dbConnect SQLException: " + e.toString()); e.printStackTrace();}	
		catch (Exception e) 					{System.out.println("dbConnect Exception: " + e.toString()); 	e.printStackTrace();}	
	}
	
	
}
