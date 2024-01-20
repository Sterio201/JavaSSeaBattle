package PlayerInterface;

import Game.Coordinate;
import Game.GameBoard;
import Game.Ship;
import Server.GameMessage;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.Socket;
import java.util.Objects;

public class JoinGame extends JFrame
{
    private static final int width = 300;
    private static final int height = 250;
    private JList<String> serverList = new JList<>(new String[]{"127.0.0.1", "192.168.1.100", "192.168.1.101", "0.0.0.0"});
    private JButton connectButton = new JButton("Подключиться");

    public JoinGame()
    {
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(width, height);
        setLocationRelativeTo(null);
        setLayout(new GridLayout(2, 1));
        add(serverList);
        add(connectButton);
        connectButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                String selectedIpAddress = serverList.getSelectedValue();
                try {
                    showNameInput(selectedIpAddress);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        setVisible(true);
    }

    public void showNameInput(String server) throws IOException
    {
        JFrame frame = new JFrame("Введите свое имя");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(300, 100);
        frame.setLocationRelativeTo(null);
        JPanel panel = new JPanel();
        JLabel nameLabel = new JLabel("Имя: ");
        JTextField nameField = new JTextField(20);
        JButton okButton = new JButton("ОК");
        okButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                String playerName = nameField.getText();
                if (playerName.isEmpty())
                {
                    JOptionPane.showMessageDialog(frame, "Введите имя");
                }
                else
                {
                    WaitingRoom waitingRoom = new WaitingRoom();

                    SwingUtilities.invokeLater(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            Socket socket = null;
                            try {
                                socket = new Socket(server, 2001);
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }

                            ObjectInputStream input = null;
                            try {
                                input = new ObjectInputStream(socket.getInputStream());
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }

                            ObjectOutputStream output = null;
                            try {
                                output = new ObjectOutputStream(socket.getOutputStream());
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }

                            try {
                                output.writeObject(new GameMessage(GameMessage.MessageType.CONNECTED, playerName ));
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }

                            ObjectInputStream finalInput = input;
                            ObjectOutputStream finalOutput = output;

                            Socket finalSocket = socket;

                            Thread clientThread = new Thread(new Runnable()
                            {
                                GameBoard boardGame;
                                @Override
                                public void run()
                                {
                                    try {
                                        while (true)
                                        {
                                            GameMessage gm = (GameMessage) finalInput.readObject();

                                            // Обработка сообщения "START_GAME"
                                            if (GameMessage.MessageType.START_GAME.equals(gm.getMessageType()))
                                            {
                                                System.out.println("Начало игры");
                                                waitingRoom.setVisible(false);
                                                boardGame = new GameBoard(finalSocket, finalOutput, finalInput);
                                            }

                                            if(GameMessage.MessageType.FIRST_TURN.equals(gm.getMessageType()) ||
                                            GameMessage.MessageType.SECOND_TURN.equals(gm.getMessageType()))
                                            {
                                                System.out.println(gm.getContent());

                                                if(Objects.equals(gm.getContent(), "Твой первый ход"))
                                                {
                                                    boardGame.isFirstTurn = true;
                                                }
                                                else
                                                {
                                                    boardGame.isFirstTurn = false;
                                                }

                                                System.out.println(boardGame.isFirstTurn);
                                                boardGame.playerID = gm.getId();
                                                boardGame.ShiftPhase(GameBoard.Phase.YOUR_PLANT_SHIPS);
                                            }

                                            if(GameMessage.MessageType.PLAY.equals(gm.getMessageType()))
                                            {
                                                if(boardGame.isFirstTurn)
                                                {
                                                    boardGame.ShiftPhase(GameBoard.Phase.YOUR_SHOT);
                                                }
                                                else
                                                {
                                                    boardGame.ShiftPhase(GameBoard.Phase.ENEMY_SHOT);
                                                }
                                            }

                                            if(GameMessage.MessageType.SHOT.equals(gm.getMessageType()))
                                            {
                                                Coordinate coord = gm.getCoordinate();
                                                boardGame.HitHandlingEnemy(coord);
                                            }

                                            if(GameMessage.MessageType.RAN.equals(gm.getMessageType()))
                                            {
                                                Coordinate coord = gm.getCoordinate();
                                                boardGame.HitHandling(coord, true);
                                            }

                                            if(GameMessage.MessageType.DESTROY_SHIP.equals(gm.getMessageType()))
                                            {
                                                Ship ship = gm.getShip();
                                                boardGame.DestroyShip(ship);
                                            }

                                            if(GameMessage.MessageType.MISS.equals(gm.getMessageType()))
                                            {
                                                Coordinate coord = gm.getCoordinate();
                                                boardGame.HitHandling(coord, false);
                                                boardGame.ShiftPhase(GameBoard.Phase.ENEMY_SHOT);
                                            }

                                            if(GameMessage.MessageType.WIN.equals(gm.getMessageType()))
                                            {
                                                boardGame.ShiftPhase(GameBoard.Phase.WIN);
                                            }

                                            System.out.println(gm.getMessageType());
                                        }
                                    } catch (IOException ex) {
                                        System.err.println("Error reading from server: " + ex.getMessage());
                                        ex.printStackTrace();
                                    } catch (ClassNotFoundException ex) {
                                        throw new RuntimeException(ex);
                                    }
                                }
                            });

                            clientThread.start();
                        }
                    });

                    frame.dispose();
                }
            }
        });
        panel.add(nameLabel);
        panel.add(nameField);
        panel.add(okButton);
        frame.getContentPane().add(panel);
        frame.setVisible(true);
    }
}