/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package itzfx;

import itzfx.data.FileUI;
import itzfx.fxml.GameObjects.MobileGoal;
import itzfx.fxml.GameObjects.StationaryGoal;
import itzfx.fxml.GameObjects.RedMobileGoal;
import itzfx.fxml.GameObjects.BlueMobileGoal;
import itzfx.fxml.GameObjects.Cone;
import itzfx.fxml.Field;
import itzfx.rerun.Command;
import itzfx.rerun.Rerun;
import itzfx.scoring.ScoreReport;
import itzfx.scoring.ScoreType;
import itzfx.scoring.Scoreable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * This class is the monitor and controller of the on-field robot. It handles
 * movement, right-click, and relates those actions to the properties of the
 * robot. It also is capable of recording autonomous routines and re-running
 * them later.
 *
 * @author Prem Chintalapudi 5776E
 */
public final class Robot extends Mobile implements Scoreable {

    private final StackPane node;
    private final StackPane realRobot;

    private double robotSpeed;
    private double robotMogoIntakeTime;
    private double robotAutostackTime;
    private double robotStatTime;
    private int robotMogoMaxStack;
    private int robotStatMaxStack;
    private boolean robotMogoFront;

    private final Hitbox hb;

    private final ObjectProperty<Paint> filter;

    private final BooleanProperty active;

    private final BooleanProperty driveBaseMovable;

    private final BooleanProperty red;

    private final ScoreReport sr;

    /**
     * Creates a robot at the specified coordinates with the specified initial
     * rotation. No layout properties (layoutX, layoutY) are adjusted, just the
     * translate properties of the node.
     *
     * @param layoutX the x coordinate to place the robot at
     * @param layoutY the y coordinate to place the robot at
     * @param initRotate the initial rotation of the robot
     */
    public Robot(double layoutX, double layoutY, double initRotate) {
        super(layoutX, layoutY, initRotate);
        node = new StackPane();
        realRobot = new StackPane();
        realRobot.setEffect(new DropShadow());
        node.getChildren().add(realRobot);
        node.setOnMouseDragged((MouseEvent m) -> super.setCenter(m.getSceneX() - 120, m.getSceneY() - 120 - 45));
        ImageView iv = new ImageView(new Image(Robot.class.getResourceAsStream("/itzfx/Images/topviewicon.png"), 90, 90, false, true));
        iv.setRotate(90);
        realRobot.getChildren().add(new Pane(iv));
        Rectangle cover = new Rectangle(90, 90);
        filter = cover.fillProperty();
        realRobot.getChildren().add(cover);
        hb = new Hitbox(45, Hitbox.CollisionType.STRONG, this, 18);
        hitboxing();
        filter.set(new Color(1, 0, 0, .03));
        red = new SimpleBooleanProperty(true);
        filter.bind(Bindings.createObjectBinding(() -> red.get() ? new Color(1, 0, 0, .05) : new Color(0, 0, 1, .05), red));
        active = new SimpleBooleanProperty(true);
        actions = new LinkedList<>();
        driveBaseMovable = new SimpleBooleanProperty(true);
        sr = new ScoreReport(this);
        sr.setScoreType(ScoreType.ZONE_NONE);
        redMogo = new RedMobileGoal(25, 45);
        blueMogo = new BlueMobileGoal(25, 45);
        node.getChildren().add(redMogo.getNode());
        node.getChildren().add(blueMogo.getNode());
        privateCone = new Cone(90, 45);
        node.getChildren().add(privateCone.getNode());
        privateCone.permaDisableCollisions();
        privateCone.vanish();
        properties();
        mogoUndo();
        linkActions();
        setController(KeyControl.Defaults.SINGLE.getKC());
        preassignValues();
    }

    private void register() {
        Field.getOwner(this).getAggregator().registerReport(sr);
    }

    private void preassignValues() {
        robotSpeed = 24;
        robotMogoIntakeTime = 2.2;
        robotAutostackTime = 2;
        robotStatTime = 2.5;
        robotMogoMaxStack = 12;
        robotStatMaxStack = 5;
    }

    /**
     * Registers the mobile goals. This method should only be called once, after
     * the robot has been added to the field.
     */
    public void registerMogos() {
        Field.getOwner(this).register(redMogo);
        Field.getOwner(this).register(blueMogo);
        register();
    }

    /**
     * Determines whether this robot has the specified mobile goal. This is
     * generally invoked by the
     * {@link Field#getOwner(itzfx.fxml.GameObjects.MobileGoal)} method to check
     * whether the mobile goal seeking an owner is in fact held by this robot on
     * a certain field.
     *
     * @param mogo the {@link MobileGoal} to test
     * @return true if this robot is holding the specified Mobile Goal
     */
    public boolean owner(MobileGoal mogo) {
        return mogo == redMogo || mogo == blueMogo;
    }

    private void mogoUndo() {
        redMogo.getNode().translateXProperty().unbind();
        blueMogo.getNode().translateXProperty().unbind();
        redMogo.permaDisableCollisions();
        blueMogo.permaDisableCollisions();
        redMogo.vanish();
        blueMogo.vanish();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DoubleBinding translateXBind() {
        return Bindings.createDoubleBinding(() -> {
            Node n = realRobot;
            return super.centerXProperty().get() - (n.getBoundsInLocal().getWidth() / 2 + n.getBoundsInLocal().getMinX());
        }, super.centerXProperty());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DoubleBinding translateYBind() {
        return Bindings.createDoubleBinding(() -> {
            Node n = realRobot;
            return super.centerYProperty().get() - (n.getBoundsInLocal().getHeight() / 2 + n.getBoundsInLocal().getMinY());
        }, super.centerYProperty());
    }

    /**
     *
     *
     * @see {@link Mobile#cleanUp}
     */
    @Override
    protected void cleanUp() {
        mogoAnimation.stop();
        stackAnimation.stop();
        if (heldMogo.get() != null) {
            heldMogo.get().setCenter(super.getCenterX() + privateMogo.get().getNode().getTranslateX() * Math.cos(Math.toRadians(node.getRotate())),
                    super.getCenterY() + privateMogo.get().getNode().getTranslateX() * Math.sin(Math.toRadians(node.getRotate())));
            heldMogo.get().reappear();
            heldMogo.set(null);
        }
        if (heldCone.get() != null) {
            heldCone.get().setCenter(super.getCenterX() + privateCone.getCenterX() * Math.cos(Math.toRadians(node.getRotate())),
                    super.getCenterY() + privateCone.getCenterX() * Math.sin(Math.toRadians(node.getRotate())));
            heldCone.get().reappear();
            heldCone.set(null);
        }
    }

    private void hitboxing() {
        hb.setXSupplier(super.centerXProperty()::get);
        hb.setYSupplier(super.centerYProperty()::get);
        node.getChildren().add(hb.getVisual());
        Hitbox.register(hb);
    }

    private void properties() {
        super.centerXProperty().addListener(Mobile.limitToField(45, centerXProperty()));
        super.centerYProperty().addListener(Mobile.limitToField(45, centerYProperty()));
        super.centerXProperty().addListener(super.exclude20(65));
        super.centerYProperty().addListener(super.exclude20(65));
        super.registerProperties();
    }

    private void linkActions() {
        actions.add(k -> forward());
        actions.add(k -> leftTurn());
        actions.add(k -> backward());
        actions.add(k -> rightTurn());
        actions.add(k -> mogo());
        actions.add(k -> autostack());
        actions.add(k -> cone());
        actions.add(k -> statStack());
        actions.add(k -> load());
    }

    private final List<Consumer<KeyCode>> actions;

    private KeyControl controller;

    /**
     * Gets the control format used by this Robot. A control format consists of
     * a set of {@link KeyCode KeyCodes} linked to specific actions. This robot
     * accepts a {@link KeyControl} as a valid control format.
     *
     * @return a KeyControl that represents this robot's control format
     */
    public KeyControl getController() {
        return controller;
    }

    /**
     * Temporarily erases the control format associated with this robot. The
     * intent of this method is to disable driver control during autonomous and
     * programming skills.
     */
    public void eraseController() {
        Iterator<KeyCode> iteratorOld = Arrays.asList(this.controller.keys()).iterator();
        actions.stream().forEach(a -> KeyBuffer.remove(iteratorOld.next(), a));
    }

    /**
     * Sets the control format used by this Robot. A control format consists of
     * a set of {@link KeyCode KeyCodes} linked to specific actions. This robot
     * accepts a {@link KeyControl} as a valid control format.
     *
     * @param controller the KeyControl to set as a control format
     */
    public void setController(KeyControl controller) {
        if (controller != null) {
            Iterator<KeyCode> iteratorNew = Arrays.asList(controller.keys()).iterator();
            if (this.controller != null) {
                Iterator<KeyCode> iteratorOld = Arrays.asList(this.controller.keys()).iterator();
                actions.stream().peek(a -> KeyBuffer.remove(iteratorOld.next(), a)).forEach(a -> KeyBuffer.register(iteratorNew.next(), a));
            } else {
                actions.stream().forEach(a -> KeyBuffer.register(iteratorNew.next(), a));
            }
            this.controller = controller;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disableCollision() {
        hb.disable();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void enableCollision() {
        hb.enable();
    }

    /**
     * {@inheritDoc}
     *
     * @return true if the robot can collide
     */
    @Override
    public boolean canCollide() {
        return hb.canCollide();
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void permaDisableCollisions() {
        Hitbox.unregister(hb);
    }

    /**
     * Sets this robot to the specified alliance.
     *
     * @param red is true if this robot is meant to be red, false if it is meant
     * to be blue
     */
    public void setRed(boolean red) {
        this.red.set(red);
    }

    /**
     *
     *
     * @see {@link Scoreable#isRed}
     */
    @Override
    public boolean isRed() {
        return red.get();
    }

    /**
     * Returns the {@link BooleanProperty} monitoring the color of this robot.
     *
     * @return the property determining the color of this robot
     */
    public BooleanProperty redProperty() {
        return red;
    }

    private final BooleanProperty recording = new SimpleBooleanProperty();

    /**
     * Every call, if this robot is recording a rerun, a new set of commands is
     * buffered to the rerun.
     */
    public void pulse() {
        if (recording.get()) {
            if (pulse.isEmpty()) {
                pulse.add(Command.NONE);
            }
            saved.add(pulse);
            pulse = new LinkedList<>();
        }
    }

    /**
     * Begins recording a rerun.
     */
    public void record() {
        pulse.clear();
        saved.clear();
        recording.set(true);
    }

    /**
     * Determines if the robot is currently recording a rerun or not.
     *
     * @return true if the robot is currently recording
     */
    public boolean isRecording() {
        return recording.get();
    }

    /**
     * Returns the {@link BooleanProperty} monitoring the recording state of
     * this robot.
     *
     * @return the property determining whether or not this robot is recording
     */
    public BooleanProperty recordingProperty() {
        return recording;
    }

    /**
     * Stops recording the current rerun.
     */
    public void stopRecording() {
        recording.set(false);
    }

    /**
     * Gets a list of strings that can be saved in a file that encode the last
     * recorded rerun.
     *
     * @return a file-worthy list of strings that can be decoded later into a
     * robot-friendly rerun code
     */
    public List<String> saveRecording() {
        if (!saved.isEmpty()) {
            while (saved.peek().get(0) == Command.NONE) {
                saved.poll();
            }
        }
        List<String> recorded = Command.encode(saved);
        saved.clear();
        return recorded;
    }

    /**
     * Sets the autonomous program to run from a list of strings representing a
     * past rerun. This is generally pulled from a rerun file (*.rrn).
     *
     * @param commands the past rerun, represented as a list of encoded strings
     */
    public void setAuton(List<String> commands) {
        rerun = new Rerun(this, commands);
    }

    private Rerun rerun;

    /**
     * Runs the saved autonomous routine.
     */
    public void runProgram() {
        if (rerun != null) {
            rerun.readBack();
        }
    }

    /**
     * Enables driver control of the robot.
     */
    public void driverControl() {
        if (rerun != null) {
            rerun.pause();
        }
        setController(controller);
    }

    /**
     * Adds "Set Autonomous", "Run Autonomous", and "Enable Driver Control"
     * options.
     *
     * @param rightClick {@inheritDoc}
     */
    @Override
    protected void rightClickOptions(ContextMenu rightClick) {
        MenuItem setAuton = new MenuItem("Set Autonomous");
        setAuton.setOnAction(e -> {
            e.consume();
            FileUI.getRerun(this, node.getScene().getWindow());
        });
        rightClick.getItems().add(setAuton);
        MenuItem runAuton = new MenuItem("Run Autonomous");
        runAuton.setOnAction(e -> {
            e.consume();
            runProgram();
        });
        rightClick.getItems().add(runAuton);
        MenuItem dc = new MenuItem("Enable Driver Control");
        dc.setOnAction(e -> {
            e.consume();
            driverControl();
        });
        rightClick.getItems().add(dc);
    }

    /**
     * Moves the robot forwards. This does take into account robot speed.
     */
    public void forward() {
        if (active.get() && driveBaseMovable.get()) {
            Platform.runLater(() -> {
                super.shiftCenter(robotSpeed / 12 * Math.cos(Math.toRadians(node.getRotate())),
                        robotSpeed / 12 * Math.sin(Math.toRadians(node.getRotate())));
            });
            if (isPrimed()) {
                Field.getOwner(this).play();
                deprime();
            }
            pulse.add(Command.FORWARD);
        }
    }

    /**
     * Moves the robot backwards. This does take into account robot speed.
     */
    public void backward() {
        if (active.get() && driveBaseMovable.get()) {
            Platform.runLater(() -> {
                super.shiftCenter(-robotSpeed / 12 * Math.cos(Math.toRadians(node.getRotate())),
                        -robotSpeed / 12 * Math.sin(Math.toRadians(node.getRotate())));
            });
            if (isPrimed()) {
                Field.getOwner(this).play();
                deprime();
            }
            pulse.add(Command.BACKWARD);
        }
    }

    /**
     * Turns the robot to the left. This does take into account robot speed.
     */
    public void leftTurn() {
        if (active.get() && driveBaseMovable.get()) {
            Platform.runLater(() -> {
                node.setRotate(node.getRotate() - robotSpeed / (Math.PI * 7));
            });
            if (isPrimed()) {
                Field.getOwner(this).play();
                deprime();
            }
            pulse.add(Command.LEFT_TURN);
        }
    }

    /**
     * MOves the robot to the right. This does take into account robot speed.
     */
    public void rightTurn() {
        if (active.get() && driveBaseMovable.get()) {
            Platform.runLater(() -> {
                node.setRotate(node.getRotate() + robotSpeed / (Math.PI * 7));
            });
            if (isPrimed()) {
                Field.getOwner(this).play();
                deprime();
            }
            pulse.add(Command.RIGHT_TURN);
        }
    }

    /**
     * Gets the robot's movement speed.
     *
     * @return the speed of the robot
     */
    public double getSpeed() {
        return robotSpeed;
    }

    private final BooleanProperty movingMogo = new SimpleBooleanProperty();

    private final Timeline mogoAnimation = new Timeline();
    private final MobileGoal redMogo;
    private final MobileGoal blueMogo;

    private final ObjectProperty<MobileGoal> privateMogo = new SimpleObjectProperty<>();
    private final ObjectProperty<MobileGoal> heldMogo = new SimpleObjectProperty<>();

    /**
     * Toggles intake/outtake of mobile goal, and attempts to do so.
     */
    public void mogo() {
        if (active.get()) {
            if (!movingMogo.get()) {
                if (heldMogo.get() == null) {
                    Platform.runLater(() -> {
                        mogoIntake();
                    });
                } else {
                    Platform.runLater(() -> {
                        mogoOuttake();
                    });
                }
                if (isPrimed()) {
                    Field.getOwner(this).play();
                    deprime();
                }
                pulse.add(Command.MOGO);
            }
        }
    }

    private void mogoIntake() {
        MobileGoal mogo = Field.getOwner(this).huntMogo(new Point2D(super.getCenterX(), super.getCenterY()),
                new Point2D(70 * Math.cos(Math.toRadians(node.getRotate())) * (robotMogoFront ? 1 : -1),
                        70 * Math.sin(Math.toRadians(node.getRotate())) * (robotMogoFront ? 1 : -1)));
        if (mogo != null) {
            privateMogo.set(mogo instanceof RedMobileGoal ? redMogo : blueMogo);
            heldMogo.set(mogo);
            mogo.vanish();
            heldMogo.get().shiftStack(privateMogo.get());
            privateMogo.get().getNode().setTranslateX(robotMogoFront ? 70 : -70);
            privateMogo.get().reappear();
            movingMogo.set(true);
            mogoAnimation.stop();
            mogoAnimation.getKeyFrames().clear();
            mogoAnimation.getKeyFrames().add(new KeyFrame(Duration.seconds(this.robotMogoIntakeTime), this::finishMogoIntake,
                    new KeyValue(privateMogo.get().getNode().translateXProperty(), robotMogoFront ? 25 : -25)));
            mogoAnimation.play();
        }
    }

    private void finishMogoIntake(ActionEvent e) {
        e.consume();
        mogoAnimation.stop();
        movingMogo.set(false);
    }

    private void mogoOuttake() {
        movingMogo.set(true);
        mogoAnimation.stop();
        mogoAnimation.getKeyFrames().clear();
        mogoAnimation.getKeyFrames().add(new KeyFrame(Duration.seconds(this.robotMogoIntakeTime), this::finishMogoOuttake,
                new KeyValue(privateMogo.get().getNode().translateXProperty(), robotMogoFront ? 70 : -70)));
        mogoAnimation.play();
    }

    private void finishMogoOuttake(ActionEvent e) {
        e.consume();
        mogoAnimation.stop();
        privateMogo.get().vanish();
        privateMogo.get().shiftStack(heldMogo.get());
        heldMogo.get().setCenter(super.getCenterX() + 70 * Math.cos(Math.toRadians(node.getRotate())) * (robotMogoFront ? 1 : -1),
                super.getCenterY() + 70 * Math.sin(Math.toRadians(node.getRotate())) * (robotMogoFront ? 1 : -1));
        heldMogo.get().reappear();
        heldMogo.set(null);
        movingMogo.set(false);
    }

    private final BooleanProperty movingCone = new SimpleBooleanProperty();

    private final Cone privateCone;
    private final ObjectProperty<Cone> heldCone = new SimpleObjectProperty<>();

    private long lastConeMove;

    /**
     * Toggles intake/outtake of a cone, and attempts to do so.
     */
    public void cone() {
        if (active.get()) {
            if (!movingCone.get() && System.currentTimeMillis() > 100 + lastConeMove) {
                lastConeMove = System.currentTimeMillis();
                if (heldCone.get() == null) {
                    Platform.runLater(() -> {
                        coneIntake();
                    });
                } else {
                    Platform.runLater(() -> {
                        coneOuttake();
                    });
                }
                if (isPrimed()) {
                    Field.getOwner(this).play();
                    deprime();
                }
                pulse.add(Command.CONE);
            }
        }
    }

    private void coneIntake() {
        Cone cone = Field.getOwner(this).huntCone(new Point2D(super.getCenterX(), super.getCenterY()),
                new Point2D(60 * Math.cos(Math.toRadians(node.getRotate())),
                        60 * Math.sin(Math.toRadians(node.getRotate()))));
        if (cone != null) {
            forceIntake(cone);
        }
    }

    private void coneOuttake() {
        privateCone.vanish();
        heldCone.get().setCenter(super.getCenterX() + 60 * Math.cos(Math.toRadians(node.getRotate())),
                super.getCenterY() + 60 * Math.sin(Math.toRadians(node.getRotate())));
        heldCone.get().reappear();
        heldCone.set(null);
    }

    private final Timeline stackAnimation = new Timeline();

    /**
     * Tries to intake a cone if none are held, and autostacks it if one is held
     * following the check.
     */
    public void autostack() {
        if (active.get() && heldMogo.get() != null && !movingCone.get() && privateMogo.get().score() / 2 < this.robotMogoMaxStack) {
            if (heldCone.get() == null) {
                Platform.runLater(() -> {
                    coneIntake();
                });
            }
            if (heldCone.get() != null) {
                Platform.runLater(() -> {
                    runAutostack();
                });
                if (isPrimed()) {
                    Field.getOwner(this).play();
                    deprime();
                }
            }
            pulse.add(Command.AUTOSTACK);
        }
    }

    private void runAutostack() {
        stackAnimation.stop();
        stackAnimation.getKeyFrames().clear();
        movingCone.set(true);
        stackAnimation.getKeyFrames().add(new KeyFrame(Duration.seconds(this.robotAutostackTime), this::finishAutostack,
                new KeyValue(privateCone.centerXProperty(), robotMogoFront ? 70 : 25)));
        stackAnimation.play();
    }

    private void finishAutostack(ActionEvent e) {
        e.consume();
        privateCone.vanish();
        stackAnimation.stop();
        movingCone.set(false);
        privateMogo.get().stack(heldCone.get());
        heldCone.set(null);
    }

    /**
     * Tries to stack a cone on a nearby stationary goal.
     */
    public void statStack() {
        if (active.get()) {
            if (!movingCone.get() && heldCone.get() != null) {
                Platform.runLater(() -> {
                    runStatStack();
                });
                if (isPrimed()) {
                    Field.getOwner(this).play();
                    deprime();
                }
            }
            pulse.add(Command.STATSTACK);
        }
    }

    private void runStatStack() {
        StationaryGoal sg = Field.getOwner(this).huntStat(new Point2D(super.getCenterX(), super.getCenterY()), new Point2D(57.5 * Math.cos(Math.toRadians(node.getRotate())),
                57.5 * Math.sin(Math.toRadians(node.getRotate()))));
        if (sg != null && sg.score() / 2 < robotStatMaxStack) {
            Point2D sgCenter = new Point2D(sg.getNode().getTranslateX() + 12.5, sg.getNode().getTranslateY() + 12.5);
            heldCone.get().setCenter(super.getCenterX() + 60 * Math.cos(Math.toRadians(node.getRotate())),
                    super.getCenterY() + 60 * Math.sin(Math.toRadians(node.getRotate())));
            privateCone.vanish();
            heldCone.get().reappear();
            heldCone.get().disableCollision();
            driveBaseMovable.set(false);
            movingCone.set(true);
            stackAnimation.stop();
            stackAnimation.getKeyFrames().clear();
            stackAnimation.getKeyFrames().add(new KeyFrame(Duration.seconds(this.robotStatTime), e -> finishStat(e, sg),
                    new KeyValue(heldCone.get().centerXProperty(), sgCenter.getX()), new KeyValue(heldCone.get().centerYProperty(), sgCenter.getY())));
            stackAnimation.play();
        }
    }

    private void finishStat(ActionEvent e, StationaryGoal sg) {
        e.consume();
        movingCone.set(false);
        stackAnimation.stop();
        driveBaseMovable.set(true);
        sg.stack(heldCone.get());
        heldCone.get().vanish();
        heldCone.set(null);
    }

    /**
     * Tries to load a driver load cone onto this robot's alliance loader.
     */
    public void load() {
        Platform.runLater(() -> {
            Field.getOwner(this).load(this);
        });
        if (isPrimed()) {
            Field.getOwner(this).play();
            deprime();
        }
        pulse.add(Command.LOAD);
    }

    /**
     * @param cone the cone to intake
     * @deprecated only public for field reset
     */
    @Deprecated
    public void forceIntake(Cone cone) {
        heldCone.set(cone);
        cone.vanish();
        privateCone.setX(90);
        privateCone.reappear();
    }

    private final BooleanProperty primed = new SimpleBooleanProperty();

    /**
     * Lets the robot know to tell the field to start its timer when this robot
     * moves.
     */
    public void prime() {
        primed.set(true);
    }

    /**
     * Determines if this robot is primed to notify the field.
     *
     * @return true if this robot is primed
     */
    public boolean isPrimed() {
        return primed.get();
    }

    /**
     * Lets the robot know that it is unnecessary to notify the field when this
     * robot moves.
     */
    public void deprime() {
        primed.set(false);
    }

    private boolean mogoWas;
    private boolean stackWas;
    private boolean rerunWas;

    /**
     * Pauses the movement of this robot, including stack and mobile goal
     * animations.
     */
    public void pause() {
        if (rerun != null && !rerun.isDone()) {
            rerun.pause();
            rerunWas = true;
        }
        if (mogoAnimation.getStatus() == Animation.Status.RUNNING) {
            mogoAnimation.pause();
            mogoWas = true;
        }
        if (stackAnimation.getStatus() == Animation.Status.RUNNING) {
            stackAnimation.pause();
            stackWas = true;
        }
        active.set(false);
    }

    /**
     * Resumes movement of this robot, including stack and mobile goal
     * animations.
     */
    public void resume() {
        if (rerunWas) {
            rerun.readBack();
        }
        if (mogoWas) {
            mogoAnimation.play();
            mogoWas = false;
        }
        if (stackWas) {
            stackAnimation.play();
            stackWas = false;
        }
        active.set(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resetProperties() {
        mogoWas = false;
        stackWas = false;
        rerunWas = false;
        mogoAnimation.stop();
        stackAnimation.stop();
        if (heldMogo.get() != null) {
            privateMogo.get().shiftStack(heldMogo.get());
            heldMogo.get().reset();
            heldMogo.set(null);
        }
        if (heldCone.get() != null) {
            heldCone.get().reset();
            heldCone.set(null);
        }
        privateCone.vanish();
        redMogo.vanish();
        blueMogo.vanish();
        movingMogo.set(false);
        movingCone.set(false);
        if (rerun != null) {
            rerun.stop();
        }
        setController(controller);
        active.set(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StackPane getNode() {
        return node;
    }

    /**
     * This method always returns 0 for a robot. However, it does force the
     * robot to update whether it is parked or not. {@inheritDoc}
     *
     * @return 0
     */
    @Override
    public int score() {
        sr.setScoreType(inParkOne() || inParkTwo() ? ScoreType.PARKING : ScoreType.ZONE_NONE);
        return 0;
    }

    private boolean inParkOne() {
        return isRed() ? super.getCenterX() < 165 && super.getCenterY() > 75 && super.getCenterY() < 285
                : super.getCenterY() < 165 && super.getCenterX() > 75 && super.getCenterX() < 285;
    }

    private boolean inParkTwo() {
        return isRed() ? super.getCenterY() > 555 && super.getCenterX() > 425 && super.getCenterX() < 645
                : super.getCenterX() > 555 && super.getCenterY() > 425 && super.getCenterY() < 645;
    }

    /**
     * Sets the values of this robot to the specified values. This is meant for
     * quickly updating this robot after a robot build session. If any values
     * are null, the previous values for those quantities are used.
     *
     * @param robotSpeed the new speed of the robot
     * @param robotMogoIntakeTime the time taken to intake or outtake a mobile
     * goal
     * @param robotAutostackTime the time taken to autostack a cone on a mobile
     * goal, on average
     * @param robotStatTime the time taken to stack a cone on a stationary goal,
     * on average
     * @param robotMaxMogo the maximum number of cones that can be stacked on a
     * mobile goal
     * @param robotMaxStat the maximum number of cones that can be stacked on a
     * stationary goal
     * @param mogoIntakeFront true if the robot intakes the mobile goal from the
     * front, false if it intakes from the back
     */
    public void acceptValues(Double robotSpeed, Double robotMogoIntakeTime, Double robotAutostackTime,
            Double robotStatTime, Integer robotMaxMogo, Integer robotMaxStat, Boolean mogoIntakeFront) {
        if (robotSpeed != null) {
            this.robotSpeed = robotSpeed;
        }
        if (robotMogoIntakeTime != null) {
            this.robotMogoIntakeTime = robotMogoIntakeTime;
        }
        if (robotAutostackTime != null) {
            this.robotAutostackTime = robotAutostackTime;
        }
        if (robotStatTime != null) {
            this.robotStatTime = robotStatTime;
        }
        if (robotMaxMogo != null) {
            this.robotMogoMaxStack = robotMaxMogo;
        }
        if (robotMaxStat != null) {
            this.robotStatMaxStack = robotMaxStat;
        }
        if (mogoIntakeFront != null) {
            if (this.robotMogoFront ^ mogoIntakeFront) {
                node.setRotate(node.getRotate() + 180);
            }
            this.robotMogoFront = mogoIntakeFront;
        }
    }

    /**
     * Gets a string that can be decoded later for the purposes of file saving.
     *
     * @return a string with all the data of this robot
     */
    public String fileData() {
        return "" + robotSpeed + " " + robotMogoIntakeTime + " "
                + robotAutostackTime + " " + robotStatTime + " "
                + robotMogoMaxStack + " " + robotStatMaxStack + " "
                + robotMogoFront;
    }

    /**
     * Fills the robot with the given data, encoded in a string originally
     * created by the {@link Robot#fileData()} method.
     *
     * @param r the robot to set the values of
     * @param fileData the encoded string
     */
    public static void fillRobot(Robot r, String fileData) {
        String[] values = fileData.split(" ");
        double rs = Double.parseDouble(values[0]);
        double rmit = Double.parseDouble(values[1]);
        double rat = Double.parseDouble(values[2]);
        double rst = Double.parseDouble(values[3]);
        int rmms = Integer.parseInt(values[4]);
        int rsms = Integer.parseInt(values[5]);
        boolean rmf = Boolean.parseBoolean(values[6]);
        r.acceptValues(rs, rmit, rat, rst, rmms, rsms, rmf);
    }

    private final Queue<List<Command>> saved = new LinkedList<>();

    private List<Command> pulse = new LinkedList<>();
}
