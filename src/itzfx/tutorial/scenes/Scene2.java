/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package itzfx.tutorial.scenes;

import itzfx.tutorial.TutorialRobot;
import itzfx.tutorial.TutorialStep;
import itzfx.tutorial.Tutorials;
import javafx.fxml.FXML;
import javafx.scene.layout.AnchorPane;

/**
 *
 * @author prem
 */
public class Scene2 implements TutorialScene {

    @FXML
    private AnchorPane root;

    private final TutorialRobot tr;

    public Scene2(TutorialRobot tr) {
        this.tr = tr;
        tr.setController(TutorialStep.STEP2.getController());
    }

    @Override
    public void init() {
        root.getChildren().add(tr.getNode());
    }

    @Override
    public void nextScene() {
        Scene3 s3 = new Scene3(tr);
        root.getScene().setRoot(Tutorials.load("scenes/Scene3.fxml", s3));
        s3.init();
        TutorialScene.setFocusedScene(s3);
    }
}
