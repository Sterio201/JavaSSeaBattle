package Server;
import Game.Coordinate;
import Game.Ship;
import Player.Player;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.lang.reflect.Array;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;

public class GameServer
{
    private ServerSocket serverSocket;
    private ArrayList<PlayerHandler> playerHandlers = new ArrayList<>();

    private int ready = 0;

    public static void main(String[] args) throws IOException {
        GameServer gameServer = new GameServer();
        gameServer.start();
    }

    public void start() throws IOException
    {
        serverSocket = new ServerSocket(2001);
        System.out.println("Server listening on port 2001");

        while (true)
        {
            Socket connection = serverSocket.accept();
            System.out.println("Connection received from: " + connection.getInetAddress().getHostAddress());

            PlayerHandler handler = new PlayerHandler(connection);
            playerHandlers.add(handler);

            Thread thread = new Thread(handler);
            thread.start();
        }
    }

    private class PlayerHandler implements Runnable {
        private Socket connection;
        private ObjectInputStream input;
        private ObjectOutputStream output;
        private String playerName;

        public PlayerHandler(Socket connection) {
            this.connection = connection;
        }

        public String getPlayerName() {
            return playerName;
        }

        @Override
        public void run() {
            try {
                output = new ObjectOutputStream(connection.getOutputStream());
                input = new ObjectInputStream(connection.getInputStream());

                GameMessage gameMessage = (GameMessage) input.readObject();
                if (gameMessage.getMessageType() == GameMessage.MessageType.CONNECTED)
                {
                    playerName = gameMessage.getContent();
                    sendToAll(new GameMessage(GameMessage.MessageType.PLAYER_ASSIGNMENT, playerName));
                    checkGameStart();
                } else {
                    throw new RuntimeException("Unexpected message type from " + playerName);
                }

                while (true)
                {
                    gameMessage = (GameMessage) input.readObject();
                    System.out.println(gameMessage.getMessageType());

                    int id_sender = gameMessage.getId();

                    switch (gameMessage.getMessageType())
                    {
                        case READY:
                            checkPlay();
                            break;

                        case SHOT:
                            Coordinate coord = gameMessage.getCoordinate();
                            sendOpponent(new GameMessage(GameMessage.MessageType.SHOT, "выстрел", coord, id_sender));
                            break;

                        case RAN:
                            Coordinate coord1 = gameMessage.getCoordinate();
                            sendOpponent(new GameMessage(GameMessage.MessageType.RAN, "попадание", coord1, id_sender));
                            break;

                        case DESTROY_SHIP:
                            Ship ship = gameMessage.getShip();
                            sendOpponent(new GameMessage(GameMessage.MessageType.DESTROY_SHIP, "убил", ship, id_sender));
                            break;

                        case MISS:
                            Coordinate coord2 = gameMessage.getCoordinate();
                            sendOpponent(new GameMessage(GameMessage.MessageType.MISS, "промах", coord2, id_sender));
                            break;

                        case LOOSE:
                            sendOpponent(new GameMessage(GameMessage.MessageType.WIN, "победа"));

                            int id_winner = 0;

                            for(int i = 0; i<playerHandlers.size(); i++)
                            {
                                if(i != id_sender)
                                {
                                    id_winner = i;
                                }
                            }

                            EndGame(playerHandlers.get(id_winner), playerHandlers.get(id_sender));

                            break;
                    }
                }
            } catch (IOException | ClassNotFoundException ex) {
                System.err.println("Error in player thread: " + ex.getMessage());
                ex.printStackTrace();
            } finally {
                try {
                    if (output != null) output.close();
                    if (input != null) input.close();
                    connection.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }

        private void sendToAll(GameMessage gameMessage) throws IOException {
            for (PlayerHandler handler : playerHandlers) {
                handler.output.writeObject(gameMessage);
            }
        }

        private void sendOpponent(GameMessage gameMessage) throws IOException
        {
            for(int i = 0; i<playerHandlers.size(); i++)
            {
                if(i != gameMessage.getId())
                {
                    playerHandlers.get(i).output.writeObject(gameMessage);
                }
            }
        }

        private void checkGameStart() throws IOException
        {
            if (playerHandlers.size() == 2)
            {
                sendToAll(new GameMessage(GameMessage.MessageType.START_GAME, "НАЧАЛО ИГРЫ"));

                int randomNumber = (int) (Math.random() * 2) + 1;

                if(randomNumber == 1)
                {
                    playerHandlers.get(0).output.writeObject(new GameMessage(GameMessage.MessageType.FIRST_TURN, "Твой первый ход", 0));
                    playerHandlers.get(1).output.writeObject(new GameMessage(GameMessage.MessageType.SECOND_TURN, "Твой второй ход", 1));
                }
                if(randomNumber == 2)
                {
                    playerHandlers.get(0).output.writeObject(new GameMessage(GameMessage.MessageType.SECOND_TURN, "Твой второй ход", 0));
                    playerHandlers.get(1).output.writeObject(new GameMessage(GameMessage.MessageType.FIRST_TURN, "Твой первый ход", 1));
                }
            }
        }

        private void checkPlay() throws IOException {
            ready++;
            if(ready == 2)
            {
                sendToAll(new GameMessage(GameMessage.MessageType.PLAY, "Поехали!"));
            }
        }

        private void EndGame(PlayerHandler winner, PlayerHandler loser)
        {
            String JDBC_DRIVER = "oracle.jdbc.driver.OracleDriver";
            String DB_URL = "jdbc:oracle:thin:@//localhost:1521/xe";

            String USER = "C##KURS";
            String PASS = "stas";

            Connection conn = null;
            PreparedStatement stmt = null;
            try {
                // Register JDBC driver
                Class.forName(JDBC_DRIVER);

                // Open a connection
                System.out.println("Connecting to database...");
                conn = DriverManager.getConnection(DB_URL, USER, PASS);

                // Prepare statement to insert a new record
                String sql = "INSERT INTO playhistory (winner, loser, gamedate) VALUES (?, ?, ?)";
                stmt = conn.prepareStatement(sql);

                // Set parameters for the statement
                stmt.setString(1, winner.playerName);
                stmt.setString(2, loser.playerName);
                stmt.setTimestamp(3, new Timestamp(new Date().getTime()));

                // Execute the statement
                int rows = stmt.executeUpdate();
                System.out.println(rows + " rows inserted.");

            } catch (ClassNotFoundException | SQLException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (stmt != null) stmt.close();
                    if (conn != null) conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}