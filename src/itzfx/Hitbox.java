/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package itzfx;

import itzfx.preload.Prestart;
import itzfx.utils.QuickMafs;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/**
 * The class that determines whether collisions should take place and how they
 * take place, to be implemented by all collidable objects.
 *
 * @author Prem Chintalapudi 5776E
 */
public final class Hitbox {

    /**
     * Whether or not the hitboxes are visible on the field. They show up as
     * green circles.
     */
    public static final BooleanProperty VISIBLE;

    private static final List<Hitbox> COLLIDABLE;

    static {
        VISIBLE = new SimpleBooleanProperty();
        COLLIDABLE = new ArrayList<>();
    }

    /**
     * Checks the list of hitboxes for any intersecting ones, then resolves the
     * collisions as set out in
     * {@link Hitbox#resolveCollision(itzfx.Hitbox, itzfx.Hitbox)}.
     */
    public static void pulse() {
        for (int i = COLLIDABLE.size() - 1; i > -1; i--) {
            Hitbox hb = COLLIDABLE.get(i);
            if (hb.cType != CollisionType.PHANTOM && hb.canCollide()) {
                COLLIDABLE.subList(0, i).parallelStream()
                        .filter(h -> h.collisionEnabled.get())
                        .filter(h -> h.check != hb.check)
                        .filter(h -> h.movableOwner != null)
                        .filter(h -> inRange(hb, h))
                        .forEach(h -> resolveCollision(hb, h));
            }
        }
    }

    private static float invMag(Point2D vector) {
        return QuickMafs.invSqRoot(QuickMafs.square((float) vector.getX()) + QuickMafs.square((float) vector.getY()));
    }

    private static void resolveCollision(Hitbox hb1, Hitbox hb2) {
        if (hb2.cType == CollisionType.PHANTOM) {
            return;
        }
        Point2D field1 = hb1.getTranslates();
        Point2D field2 = hb2.getTranslates();
        Point2D approach = field2.subtract(field1);
        float invMagnitude = invMag(approach);
        float dist = (float) hb1.visual.getRadius() + (float) hb2.visual.getRadius() - 1 / invMagnitude;
        Point2D approach0 = approach.multiply(invMagnitude);
        Platform.runLater(() -> {
            try {
                float collisionFactor = 1;
                if (hb1.cType == CollisionType.WEAK || hb2.cType == CollisionType.WEAK) {
                    collisionFactor = .02f;
                }
                if (hb1.mass == Float.POSITIVE_INFINITY) {
                    if (hb2.movableOwner != null) {
                        hb2.movableOwner.shiftCenter((float) approach0.getX() * dist * collisionFactor, (float) approach0.getY() * dist * collisionFactor);
                    }
                } else if (hb2.mass == Float.POSITIVE_INFINITY) {
                    if (hb1.movableOwner != null) {
                        hb1.movableOwner.shiftCenter((float) -approach0.getX() * dist * collisionFactor, (float) -approach0.getY() * dist * collisionFactor);
                    }
                } else {
                    float rr1 = hb2.mass / (hb1.mass + hb2.mass);
                    float rr2 = 1 - rr1;
                    hb1.movableOwner.shiftCenter(-rr1 * (float) approach0.getX() * dist * collisionFactor, -rr1 * (float) approach0.getY() * dist * collisionFactor);
                    hb2.movableOwner.shiftCenter(rr2 * (float) approach0.getX() * dist * collisionFactor, rr2 * (float) approach0.getY() * dist * collisionFactor);
                }
            } catch (Exception ex) {
            }
        });
    }

    private static boolean inRange(Hitbox hb1, Hitbox hb2) {
        try {
            return (squareDistance(hb1.getTranslates(), hb2.getTranslates()) < Math.pow(hb1.visual.getRadius() + hb2.visual.getRadius(), 2));
        } catch (Throwable t) {
            return false;
        }
    }

    private static float squareDistance(Point2D point1, Point2D point2) {
        return (float) QuickMafs.square((float) point1.getX() - (float) point2.getX()) + (float) QuickMafs.square((float) point1.getY() - (float) point2.getY());
    }

    /**
     * Registers a hitbox in a list of hitboxes to check for collisions. This
     * method <b>must</b> be called for a hitbox to exhibit proper collision
     * behavior.
     *
     * @param hb the hitbox to register
     */
    public static void register(Hitbox hb) {
        try {
            COLLIDABLE.add(hb);
        } catch (Exception ex) {
        }
    }

    /**
     * Removes a hitbox from a list of hitboxes checked for collisions.
     *
     * @param hb the hitbox to remove
     */
    public static void unregister(Hitbox hb) {
        try {
            COLLIDABLE.remove(hb);
        } catch (Exception ex) {
        }
    }

    /**
     * Clears the hitbox list such that no collisions are checked among
     * registered hitboxes ever.
     */
    public static void clear() {
        COLLIDABLE.clear();
    }

    private final Circle visual;

    private final BooleanProperty collisionEnabled;

    private Hitbox(float radius, CollisionType cType, Mobile movableOwner, Node check, float mass) {
        visual = new Circle(radius, Color.LIME);
        collisionEnabled = new SimpleBooleanProperty(true);
        visual.visibleProperty().bind(VISIBLE.and(collisionEnabled));
        this.cType = cType;
        this.mass = mass;
        visual.setMouseTransparent(true);
        this.movableOwner = movableOwner;
        this.check = check;
    }

    /**
     * Constructs a hitbox with the given properties.
     *
     * @param radius the radius of the hitbox
     * @param cType the type of collision this hitbox will perform
     * @param check the node to check to prevent hitboxes of the same owner from
     * colliding with each other.
     * @param mass the relative mass of the object
     */
    public Hitbox(float radius, CollisionType cType, Node check, float mass) {
        this(radius, cType, null, check, mass);
    }

    /**
     * Constructs a hitbox with the given properties.
     *
     * @param radius the radius of the hitbox
     * @param cType the type of collision this hitbox will perform
     * @param movableOwner the {@link Mobile mobile object} to shift in response
     * @param mass the relative mass of the object
     */
    public Hitbox(float radius, CollisionType cType, Mobile movableOwner, float mass) {
        this(radius, cType, movableOwner, movableOwner.getNode(), mass);
    }

    private Supplier<Float> xPos;
    private Supplier<Float> yPos;

    /**
     * Sets the supplier of the x coordinate of a hitbox during collision
     * determination.
     *
     * @param xPos the x coordinate supplier
     */
    public void setXSupplier(Supplier<Float> xPos) {
        this.xPos = xPos;
    }

    /**
     * Sets the supplier of the y coordinate of a hitbox during collision
     * determination.
     *
     * @param yPos the y coordinate supplier
     */
    public void setYSupplier(Supplier<Float> yPos) {
        this.yPos = yPos;
    }

    private Point2D getTranslates() {
        return new Point2D(xPos == null ? check.getTranslateX() + visual.getRadius() : (xPos.get()),
                yPos == null ? check.getTranslateY() + visual.getRadius() : (yPos.get()));
    }

    private final Mobile movableOwner;
    private final Node check;

    private CollisionType cType;

    private final float mass;

    /**
     * Whether or not this hitbox is allowed to collide. This is used for
     * temporary collision prevention.
     *
     * @return true if this hitbox will collide, false if not.
     */
    public boolean canCollide() {
        return collisionEnabled.get();
    }

    /**
     * Temporarily disables this hitbox's collision properties.
     */
    public void disable() {
        collisionEnabled.set(false);
    }

    /**
     * Enables this hitbox's collision properties.
     */
    public void enable() {
        collisionEnabled.set(true);
    }

    /**
     * Gets the mobile object that spawned this hitbox. If this is null, the
     * object that spawned this hitbox is not mobile.
     *
     * @return the mobile owner
     */
    public Mobile getMovable() {
        return this.movableOwner;
    }

    /**
     * Determines whether this hitbox can move or not. If this hitbox has a
     * movable owner, it is movable. If not, it is not.
     *
     * @return true if this hitbox can be moved.
     */
    public boolean isMoveable() {
        return movableOwner != null;
    }

    /**
     * Returns a circle that represents the activity of a hitbox. The circle's
     * visibility is controlled by {@link Hitbox#VISIBLE}, so any changes to the
     * state of that {@link BooleanProperty} are reflected in whether or not
     * this hitbox renders. In addition, disabled hitboxes are not rendered.
     *
     * @return the visual circle representing this hitbox's area.
     */
    public Circle getVisual() {
        return visual;
    }

    /**
     * An enumeration dictating the types of collisions that can occur.
     */
    public static enum CollisionType {

        /**
         * Another STRONG hitbox must attempt to avoid intersecting this one at
         * all costs. This is meant to prevent phasing of objects.
         */
        STRONG,
        /**
         * Another STRONG hitbox must attempt to avoid intersecting this one,
         * but may pass through given enough effort. This is meant to allow
         * resistance to objects, but not total motion prevention.
         */
        WEAK,
        /**
         * No collision at all. This is rarely used, as it is simpler to either
         * disable the hitbox or to unregister it altogether.
         */
        PHANTOM;
    }
}
