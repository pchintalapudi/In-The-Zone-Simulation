/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package itzfx.fxml;

import itzfx.Robot;
import itzfx.data.FileUI;
import itzfx.fxml.build.RobotBuilder;
import itzfx.fxml.controller.KeyBinder;
import itzfx.rerun.Translate;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Menu;
import javafx.scene.control.TabPane;
import javafx.scene.layout.AnchorPane;

/**
 * FXML Controller class. Controls the "RobotMenu.fxml" file. This is repeated 4
 * times on a fully formed field, all headed under the "Robot" top-level menu.
 *
 * @author Prem Chintalapudi 5776E
 */
public class RobotMenu {

    private final Robot r;

    @FXML
    private Menu menu;

    private final String menuText;

    /**
     * Creates a new RobotMenu with the associated {@link Robot Robot} and text.
     * The menu's text will be initialized with the provided menutext.
     *
     * @param r the robot controlled by this menu
     * @param menuText the text displayed on this menu
     */
    public RobotMenu(Robot r, String menuText) {
        this.r = r;
        this.menuText = menuText;
    }

    @FXML
    private void initialize() {
        this.menu.setText(menuText);
    }

    @FXML
    private void build() {
        FXMLLoader loader = new FXMLLoader(FXMLController.class.getResource("/itzfx/fxml/build/RobotBuilder.fxml"));
        try {
            TabPane p = loader.load();
            RobotBuilder rb = loader.getController();
            Alert show = new Alert(Alert.AlertType.INFORMATION, "", ButtonType.APPLY);
            show.getDialogPane().setContent(p);
            show.getDialogPane().setPrefHeight(500);
            show.showAndWait().filter(bt -> bt.getButtonData().equals(ButtonBar.ButtonData.APPLY)).ifPresent(bt -> {
                rb.submit();
                rb.fillRobot(r);
                FileUI.saveRobot(r, r.getNode().getScene().getWindow());
            });
        } catch (IOException ex) {
            Logger.getLogger(FXMLController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @FXML
    private void load() {
        FileUI.fillRobot(r, r.getNode().getScene().getWindow());
    }
}
