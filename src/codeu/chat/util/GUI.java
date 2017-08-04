package codeu.chat.util;

import codeu.chat.client.commandline.Chat;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Created by Jiahui Chen on 7/30/2017.
 */
public class GUI {

    public static void launchGUI(Chat chat) {
        JFrame window = new JFrame("CodeU Chat App");
        window.setSize(700, 700);

        //Overall panel that will hold all smaller panels
        JPanel backingPanel = new JPanel();
        backingPanel.setLayout(new BorderLayout(0, 0));
        window.add(backingPanel);
        window.setContentPane(backingPanel);

        //Panel to hold top/main panels containing chat text and user buttons
        // panels will be switched based on where the user is in the chat app
        JPanel switchPanel = new JPanel(new CardLayout());
        CardLayout panelSwitcher = new CardLayout();
        switchPanel.setLayout(panelSwitcher);
        backingPanel.add(switchPanel, BorderLayout.CENTER);

        //Root panel holding root panel's GUI
        JPanel rootPanel = new JPanel();
        rootPanel.setLayout(new GridBagLayout());
        GridBagConstraints rootConstraints = new GridBagConstraints();
        rootConstraints.fill = GridBagConstraints.BOTH;
        switchPanel.add(rootPanel, "rootPanel");
        panelSwitcher.show(switchPanel, "rootPanel");

        JTextArea rootTextDisplay = new JTextArea("Application Activity:\nType in input text box and press a command button.", 20, 20);
        rootTextDisplay.setLineWrap(true);
        JScrollPane rootScroll = new JScrollPane(rootTextDisplay);
        PrintStream rootOutput = new PrintStream(new GUIOutputDisplay(rootTextDisplay));
        System.setOut(rootOutput);
        System.setErr(rootOutput);
        rootConstraints.gridx = 1;
        rootConstraints.gridy = 0;
        rootConstraints.gridwidth = 2;
        rootConstraints.gridheight = 2;
        rootPanel.add(rootScroll, rootConstraints);

        //Panel with command buttons and text input
        JPanel rootInputPanel = new JPanel();
        rootInputPanel.setLayout(new GridLayout(2, 1));
        rootConstraints.gridx = 1;
        rootConstraints.gridy = 2;
        rootConstraints.gridwidth = 2;
        rootConstraints.gridheight = 1;
        rootPanel.add(rootInputPanel, rootConstraints);

        //Text input to sign in/add user
        JTextField rootTextInput = new JTextField(1);
        rootInputPanel.add(rootTextInput);

        //Command buttons
        JPanel rootButtonPanel = new JPanel();
        rootButtonPanel.setLayout(new GridLayout(1, 6));
        rootInputPanel.add(rootButtonPanel);
        JButton rootHelp = new JButton("Help");
        rootHelp.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                chat.handleCommand("help");
            }

        });
        rootButtonPanel.add(rootHelp);
        JButton listUsers = new JButton("List Users");
        listUsers.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chat.handleCommand("u-list");
            }
        });
        rootButtonPanel.add(listUsers);
        JButton addUser = new JButton("Add User");
        addUser.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chat.handleCommand("u-add " + rootTextInput.getText());
            }
        });
        rootButtonPanel.add(addUser);
        JButton info = new JButton("Info");
        info.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chat.handleCommand("info");
            }
        });
        rootButtonPanel.add(info);
        JButton exit = new JButton("Exit");
        ActionListener exitListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                chat.handleCommand("exit");
                window.dispose();
                System.exit(0);
            }
        };
        exit.addActionListener(exitListener);
        rootButtonPanel.add(exit);


        //User panel holding user panel's GUI, same as root panel but different buttons
        JPanel userPanel = new JPanel();
        userPanel.setLayout(new GridBagLayout());
        GridBagConstraints userConstraints = new GridBagConstraints();
        userConstraints.fill = GridBagConstraints.BOTH;
        switchPanel.add(userPanel, "userPanel");
//	    panelSwitcher.show(switchPanel, "userPanel");

        //text display window in user panel, displays output of application
        JTextArea userTextDisplay = new JTextArea("Application Activity:\nType in input text box and press a command button.", 20, 20);
        userTextDisplay.setLineWrap(true);
        userTextDisplay.setLayout(new FlowLayout());
        PrintStream userOutput = new PrintStream(new GUIOutputDisplay(userTextDisplay));
        JScrollPane userScroll = new JScrollPane(userTextDisplay);

        //sign in button added here since userOutput stream must be used when user switches panels
        JButton signIn = new JButton("Sign In");
        signIn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                chat.handleCommand("u-sign-in " + rootTextInput.getText());
                panelSwitcher.show(switchPanel, "userPanel");
                System.setOut(userOutput);
                System.setErr(userOutput);
            }

        });
        rootButtonPanel.add(signIn);

        userTextDisplay.setLayout(new FlowLayout());
        userConstraints.gridx = 1;
        userConstraints.gridy = 0;
        userConstraints.gridwidth = 2;
        userConstraints.gridheight = 2;
        userPanel.add(userScroll, userConstraints);

        //Panel with command buttons and text input
        JPanel userInputPanel = new JPanel();
        userInputPanel.setLayout(new GridLayout(2, 1));
        userConstraints.gridx = 1;
        userConstraints.gridy = 2;
        userConstraints.gridwidth = 2;
        userConstraints.gridheight = 1;
        userPanel.add(userInputPanel, userConstraints);

        //Text input for user panel commands
        JTextField userTextInput = new JTextField(1);
        userInputPanel.add(userTextInput);

        //Command buttons
        JPanel userButtonPanel = new JPanel();
        userButtonPanel.setLayout(new GridLayout(2, 7));
        userInputPanel.add(userButtonPanel);
        JButton userHelp = new JButton("Help");
        userHelp.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                chat.handleCommand("help");
            }

        });
        userButtonPanel.add(userHelp);
        JButton listConvos = new JButton("List Chats");
        listConvos.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chat.handleCommand("c-list");
            }
        });
        userButtonPanel.add(listConvos);
        JButton listInterestConvos = new JButton("List Interest Chats");
        listInterestConvos.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chat.handleCommand("c-interest-list");
            }
        });
        userButtonPanel.add(listInterestConvos);
        JButton addInterestConvo = new JButton("Add Interest Chat");
        addInterestConvo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chat.handleCommand("c-interest-add " + userTextInput.getText());
            }
        });
        userButtonPanel.add(addInterestConvo);
        JButton removeInterestConvo = new JButton("Remove Interest Chat");
        removeInterestConvo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chat.handleCommand("c-interest-remove " + userTextInput.getText());
            }
        });
        userButtonPanel.add(removeInterestConvo);
        JButton listInterestUsers = new JButton("List Interest Users");
        listInterestUsers.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chat.handleCommand("u-interest-list");
            }
        });
        userButtonPanel.add(listInterestUsers);
        JButton addInterestUser = new JButton("Add Interest User");
        addInterestUser.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chat.handleCommand("u-interest-add " + userTextInput.getText());
            }
        });
        userButtonPanel.add(addInterestUser);
        JButton removeInterestUser = new JButton("Remove Interest User");
        removeInterestUser.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chat.handleCommand("u-interest-remove " + userTextInput.getText());
            }
        });
        userButtonPanel.add(removeInterestUser);
        JButton statusUpdate = new JButton("Status Update");
        userButtonPanel.add(statusUpdate);
        statusUpdate.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chat.handleCommand("status-update");
            }
        });
        JButton userInfo = new JButton("Info");
        userInfo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chat.handleCommand("info");
            }
        });
        userButtonPanel.add(userInfo);
        JButton userBack = new JButton("Back");
        userBack.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                chat.handleCommand("back");
                System.setErr(rootOutput);
                System.setOut(rootOutput);
                panelSwitcher.show(switchPanel, "rootPanel");
            }

        });
        userButtonPanel.add(userBack);
        JButton userExit = new JButton("Exit");
        userExit.addActionListener(exitListener);
        userButtonPanel.add(userExit);


        //Conversation panel holding conversation panel's GUI, same as root panel but different buttons
        JPanel convoPanel = new JPanel();
        convoPanel.setLayout(new GridBagLayout());
        GridBagConstraints convoConstraints = new GridBagConstraints();
        convoConstraints.fill = GridBagConstraints.BOTH;
        switchPanel.add(convoPanel, "convoPanel");
//		panelSwitcher.show(switchPanel, "convoPanel");

        //message & text display window in convo panel, should display any messages/output while in convo panel
        JTextArea messages = new JTextArea("Application Activity:\nType in input text box and press a command button.", 20, 20);
        messages.setLineWrap(true);
        messages.setLayout(new FlowLayout());
        PrintStream convoOutput = new PrintStream(new GUIOutputDisplay(messages));
        JScrollPane convoScroll = new JScrollPane(messages);

        JButton addConvo = new JButton("Add Chat");
        addConvo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chat.handleCommand("c-add " + userTextInput.getText());
                System.setOut(convoOutput);
                System.setErr(convoOutput);
                panelSwitcher.show(switchPanel, "convoPanel");
            }
        });
        userButtonPanel.add(addConvo);
        JButton joinConvo = new JButton("Join Chat");
        joinConvo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                chat.handleCommand("c-join " + userTextInput.getText());
                System.setOut(convoOutput);
                System.setErr(convoOutput);
                panelSwitcher.show(switchPanel, "convoPanel");
            }

        });
        userButtonPanel.add(joinConvo);

        convoConstraints.gridx = 1;
        convoConstraints.gridy = 0;
        convoConstraints.gridwidth = 2;
        convoConstraints.gridheight = 2;
        convoPanel.add(convoScroll, convoConstraints);

        //Panel with command buttons and text input
        JPanel convoInputPanel = new JPanel();
        convoInputPanel.setLayout(new GridLayout(2, 1));
        convoConstraints.gridx = 1;
        convoConstraints.gridy = 2;
        convoConstraints.gridwidth = 2;
        convoConstraints.gridheight = 1;
        convoPanel.add(convoInputPanel, convoConstraints);

        //Text input for user's messages/other conversation commands
        JTextField convoTextInput = new JTextField(1);
        convoInputPanel.add(convoTextInput);

        //Command buttons
        JPanel convoButtonPanel = new JPanel();
        convoButtonPanel.setLayout(new GridLayout(1, 9));
        convoInputPanel.add(convoButtonPanel);
        JButton convoHelp = new JButton("Help");
        convoHelp.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chat.handleCommand("help");
            }
        });
        convoButtonPanel.add(convoHelp);
        JButton addMessage = new JButton("Add Message");
        addMessage.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chat.handleCommand("m-add " + convoTextInput.getText());
            }
        });
        convoButtonPanel.add(addMessage);
        JButton addMember = new JButton("Add Member");
        addMember.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chat.handleCommand("u-add-member " + convoTextInput.getText());
            }
        });
        convoButtonPanel.add(addMember);
        JButton removeMember = new JButton("Remove Member");
        removeMember.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chat.handleCommand("u-remove-member " + convoTextInput.getText());
            }
        });
        convoButtonPanel.add(removeMember);
        JButton removeOwner = new JButton("Remove Owner");
        removeOwner.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chat.handleCommand("u-remove-owner " + convoTextInput.getText());
            }
        });
        convoButtonPanel.add(removeOwner);
        JButton addOwner = new JButton("Add Owner");
        addOwner.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chat.handleCommand("u-add-owner " + convoTextInput.getText());
            }
        });
        convoButtonPanel.add(addOwner);
        JButton convoInfo = new JButton("Info");
        convoInfo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chat.handleCommand("info");
            }
        });
        convoButtonPanel.add(convoInfo);
        JButton convoBack = new JButton("Back");
        convoBack.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                chat.handleCommand("back");
                System.setOut(userOutput);
                System.setErr(userOutput);
                panelSwitcher.show(switchPanel, "userPanel");
            }

        });
        convoButtonPanel.add(convoBack);
        JButton convoExit = new JButton("Exit");
        convoExit.addActionListener(exitListener);
        convoButtonPanel.add(convoExit);

        window.pack();
        window.setVisible(true);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }
}

/**
 * Created by Jiahui Chen on 8/2/2017.
 * Creates a custom output stream that will display to the GUI's text panel.
 */
class GUIOutputDisplay extends OutputStream {
    private JTextArea textDisplay;

    public GUIOutputDisplay(JTextArea textArea){
        textDisplay = textArea;
    }

    //redirects console/terminal output to JTextArea
    @Override
    public void write(int b) throws IOException {
        textDisplay.append(String.valueOf((char) b));
    }
}
