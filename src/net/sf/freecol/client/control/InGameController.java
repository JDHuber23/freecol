/**
 *  Copyright (C) 2002-2014   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.client.control;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

import net.sf.freecol.FreeCol;
import net.sf.freecol.client.ClientOptions;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.client.gui.i18n.Messages;
import net.sf.freecol.client.gui.option.FreeColActionUI;
import net.sf.freecol.client.gui.panel.ChoiceItem;
import net.sf.freecol.common.debug.DebugUtils;
import net.sf.freecol.common.debug.FreeColDebugger;
import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.model.Ability;
import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.AbstractUnit;
import net.sf.freecol.common.model.BuildableType;
import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.ColonyTile;
import net.sf.freecol.common.model.ColonyWas;
import net.sf.freecol.common.model.DiplomaticTrade;
import net.sf.freecol.common.model.DiplomaticTrade.TradeContext;
import net.sf.freecol.common.model.DiplomaticTrade.TradeStatus;
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.EuropeWas;
import net.sf.freecol.common.model.Event;
import net.sf.freecol.common.model.FoundingFather;
import net.sf.freecol.common.model.FreeColGameObject;
import net.sf.freecol.common.model.FreeColObject;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GameOptions;
import net.sf.freecol.common.model.GoldTradeItem;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsContainer;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.HighScore;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.InciteTradeItem;
import net.sf.freecol.common.model.Limit;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.LostCityRumour;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Map.Direction;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.ModelMessage.MessageType;
import net.sf.freecol.common.model.Monarch.MonarchAction;
import net.sf.freecol.common.model.Nameable;
import net.sf.freecol.common.model.NationSummary;
import net.sf.freecol.common.model.Ownable;
import net.sf.freecol.common.model.PathNode;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Player.NoClaimReason;
import net.sf.freecol.common.model.Player.PlayerType;
import net.sf.freecol.common.model.Player.Stance;
import net.sf.freecol.common.model.ProductionType;
import net.sf.freecol.common.model.Region;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.StanceTradeItem;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tension;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileImprovementType;
import net.sf.freecol.common.model.TradeRoute;
import net.sf.freecol.common.model.TradeRouteStop;
import net.sf.freecol.common.model.TransactionListener;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.Unit.UnitState;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.model.UnitTypeChange.ChangeType;
import net.sf.freecol.common.model.UnitWas;
import net.sf.freecol.common.model.WorkLocation;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.common.networking.NetworkConstants;
import net.sf.freecol.common.networking.ServerAPI;
import net.sf.freecol.common.option.BooleanOption;
import net.sf.freecol.server.FreeColServer;


/**
 * The controller that will be used while the game is played.
 */
public final class InGameController implements NetworkConstants {

    private static final Logger logger = Logger.getLogger(InGameController.class.getName());

    /**
     * Selecting next unit depends on mode--- either from the active list,
     * from the going-to list, or flush going-to and end the turn.
     */
    private static enum MoveMode {
        NEXT_ACTIVE_UNIT,
        EXECUTE_GOTO_ORDERS,
        END_TURN;

        public MoveMode maximize(MoveMode m) {
            return (this.ordinal() < m.ordinal()) ? m : this;
        }
    }

    private static final short UNIT_LAST_MOVE_DELAY = 300;

    /** A template to use as a magic cookie for aborted trades. */
    private static final StringTemplate abortTrade
        = StringTemplate.template("");

    /** The enclosing <code>FreeColClient</code>. */
    private final FreeColClient freeColClient;

    /** A cache reference to the gui. */
    private GUI gui;

    /** Current mode for moving units. */
    private MoveMode moveMode = MoveMode.NEXT_ACTIVE_UNIT;

    /** A map of messages to be ignored. */
    private HashMap<String, Integer> messagesToIgnore = new HashMap<>();

    /** The messages in the last turn report. */
    private final List<ModelMessage> turnReportMessages = new ArrayList<>();


    /**
     * The constructor to use.
     *
     * @param freeColClient The <code>FreeColClient</code> for the game.
     */
    public InGameController(FreeColClient freeColClient) {
        this.freeColClient = freeColClient;
        this.gui = freeColClient.getGUI();

        // FIXME: fetch value of lastSaveGameFile from a persistent
        // client value
        //   lastSaveGameFile = new File(freeColClient.getClientOptions().getString(null));
    }


    // Simple utilities

    /**
     * Meaningfully named access to the ServerAPI.
     *
     * @return The ServerAPI.
     */
    private ServerAPI askServer() {
        return freeColClient.askServer();
    }

    /**
     * Gets the specification for the current game.
     *
     * @return The current game specification.
     */
    private Specification getSpecification() {
        return freeColClient.getGame().getSpecification();
    }

    /**
     * Require that it is this client's player's turn.
     * Put up the notYourTurn message if not.
     *
     * @return True if it is our turn.
     */
    private boolean requireOurTurn() {
        if (!freeColClient.currentPlayerIsMyPlayer()) {
            if (freeColClient.isInGame()) {
                gui.showInformationMessage("notYourTurn");
            }
            return false;
        }
        return true;
    }

    /**
     * Convenience function to find an adjacent settlement.  Intended
     * to be called in contexts where we are expecting a settlement to
     * be there, such as when handling a particular move type.
     *
     * @param tile The <code>Tile</code> to start at.
     * @param direction The <code>Direction</code> to step.
     * @return A settlement on the adjacent tile if any.
     */
    private Settlement getSettlementAt(Tile tile, Direction direction) {
        return tile.getNeighbourOrNull(direction).getSettlement();
    }

    /**
     * Convenience function to find the nation controlling an adjacent
     * settlement.  Intended to be called in contexts where we are
     * expecting a settlement or unit to be there, such as when
     * handling a particular move type.
     *
     * @param tile The <code>Tile</code> to start at.
     * @param direction The <code>Direction</code> to step.
     * @return The name of the nation controlling a settlement on the
     *         adjacent tile if any.
     */
    private StringTemplate getNationAt(Tile tile, Direction direction) {
        Tile newTile = tile.getNeighbourOrNull(direction);
        Player player = null;
        if (newTile.hasSettlement()) {
            player = newTile.getSettlement().getOwner();
        } else if (newTile.getFirstUnit() != null) {
            player = newTile.getFirstUnit().getOwner();
        } else { // should not happen
            player = freeColClient.getGame().getUnknownEnemy();
        }
        return player.getNationName();
    }

    /**
     * Updates the GUI after a unit moves.
     */
    private void updateAfterMove() {
        SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    freeColClient.updateActions();
                    gui.updateMenuBar();
                }
            });
    }

    // Server access routines called from multiple places.

    /**
     * Claim a tile.
     *
     * @param player The <code>Player</code> that is claiming.
     * @param tile The <code>Tile</code> to claim.
     * @param claimant The <code>Unit</code> or <code>Colony</code> claiming.
     * @param price The price required.
     * @return True if the claim succeeded.
     */
    private boolean askClaimTile(Player player, Tile tile,
                                 FreeColGameObject claimant, int price) {
        final Player owner = tile.getOwner();
        if (price < 0) { // not for sale
            return false;
        } else if (price > 0) { // for sale
            GUI.ClaimAction act = gui.getClaimChoice(tile, player, price,
                                                     owner);
            if (act == null) return false; // Cancelled
            switch (act) {
            case ACCEPT: // accepted price
                break;
            case STEAL:
                price = NetworkConstants.STEAL_LAND;
                break;
            default:
                logger.warning("Claim dialog fail: " + act);
                return false;
            }
        } // else price == 0 and we can just proceed to claim

        // Ask the server
        boolean ret = askServer().claimTile(tile, claimant, price)
            && player.owns(tile);
        if (ret) {
            updateAfterMove();
        }
        return ret;
    }

    /**
     * A unit in Europe emigrates.
     *
     * @param europe The <code>Europe</code> where the unit appears.
     * @param slot The slot to choose.
     * @return The new <code>Unit</code> or null on failure.
     */
    private Unit askEmigrate(Europe europe, int slot) {
        EuropeWas europeWas = new EuropeWas(europe);
        Unit ret = null;
        if (askServer().emigrate(slot)
            && (ret = europeWas.getNewUnit()) != null) {
            europeWas.fireChanges();
            gui.updateMenuBar();
        }
        return ret;
    }

    /**
     * Load some goods onto a carrier.
     *
     * @param goods The <code>Goods</code> to load.
     * @param carrier The <code>Unit</code> to load onto.
     * @return True if the load succeeded.
     */
    private boolean askLoadGoods(Goods goods, Unit carrier) {
        if (carrier.isInEurope() && goods.getLocation() instanceof Europe) {
            if (!carrier.getOwner().canTrade(goods.getType())) return false;
            return buyGoods(goods.getType(), goods.getAmount(), carrier);
        }

        GoodsType type = goods.getType();
        int oldAmount = carrier.getGoodsContainer().getGoodsCount(type);
        UnitWas unitWas = new UnitWas(carrier);
        Colony colony = carrier.getColony();
        ColonyWas colonyWas = (colony == null) ? null : new ColonyWas(colony);
        boolean ret = askServer().loadCargo(goods, carrier)
            && carrier.getGoodsContainer().getGoodsCount(type) != oldAmount;
        if (ret) {
            if (colonyWas != null) colonyWas.fireChanges();
            unitWas.fireChanges();
            gui.playSound("sound.event.loadCargo");
        }
        return ret;
    }

    /**
     * Sell some goods from a carrier.
     *
     * @param goods The <code>Goods</code> to unload.
     * @param carrier The <code>Unit</code> carrying the goods.
     * @param europe The <code>Europe</code> to sell to.
     * @return True if the unload succeeded.
     */
    private boolean askSellGoods(Goods goods, Unit carrier, Europe europe) {
        // Try to sell.  Remember a bunch of stuff first so the transaction
        // can be logged.
        final Player player = freeColClient.getMyPlayer();
        final Market market = player.getMarket();
        final GoodsType type = goods.getType();
        final int amount = goods.getAmount();
        final int price = market.getPaidForSale(type);
        final int tax = player.getTax();
        final int oldAmount = carrier.getGoodsContainer().getGoodsCount(type);

        UnitWas unitWas = new UnitWas(carrier);
        EuropeWas europeWas = new EuropeWas(europe);
        boolean ret = askServer().sellGoods(goods, carrier)
            && carrier.getGoodsContainer().getGoodsCount(type) != oldAmount;
        if (ret) {
            unitWas.fireChanges();
            europeWas.fireChanges();
            for (TransactionListener l : market.getTransactionListener()) {
                l.logSale(type, amount, price, tax);
            }
            gui.playSound("sound.event.sellCargo");
            gui.updateMenuBar();
            nextModelMessage();
        }
        return ret;
    }

    /**
     * Set a destination for a unit.
     *
     * @param unit The <code>Unit</code> to direct.
     * @param destination The destination <code>Location</code>.
     * @return True if the destination was set.
     */
    private boolean askSetDestination(Unit unit, Location destination) {
        return askServer().setDestination(unit, destination)
            && unit.getDestination() == destination;
    }

    /**
     * Unload some goods from a carrier.
     *
     * @param goods The <code>Goods</code> to unload.
     * @param carrier The <code>Unit</code> carrying the goods.
     * @param colony The <code>Colony</code> to unload to.
     * @return True if the unload succeeded.
     */
    private boolean askUnloadGoods(Goods goods, Unit carrier, Colony colony) {
        GoodsType type = goods.getType();
        int oldAmount = carrier.getGoodsContainer().getGoodsCount(type);
        ColonyWas colonyWas = new ColonyWas(colony);
        UnitWas unitWas = new UnitWas(carrier);
        boolean ret = askServer().unloadCargo(goods)
            && carrier.getGoodsContainer().getGoodsCount(type) != oldAmount;
        if (ret) {
            colonyWas.fireChanges();
            unitWas.fireChanges();
            gui.playSound("sound.event.unloadCargo");
        }
        return ret;
    }


    // Utilities connected with saving the game

    /**
     * Returns a string representation of the given turn suitable for
     * savegame files.
     *
     * @param turn a <code>Turn</code> value
     * @return A string with the format: "<i>[season] year</i>".
     *         Examples: "1602_1_Spring", "1503"...
     */
    private String getSaveGameString(Turn turn) {
        int year = turn.getYear();
        switch (turn.getSeason()) {
        case SPRING:
            return Integer.toString(year) + "_1_" + Messages.message("spring");
        case AUTUMN:
            return Integer.toString(year) + "_2_" + Messages.message("autumn");
        case YEAR: default:
            return Integer.toString(year);
        }
    }

    /**
     * Creates at least one autosave game file of the currently played
     * game in the autosave directory.  Does nothing if there is no
     * game running.
     */
    private void autoSaveGame () {
        final Game game = freeColClient.getGame();
        final ClientOptions options = freeColClient.getClientOptions();
        if (game == null) return;

        // unconditional save per round (fixed file "last-turn")
        String prefix = options.getText(ClientOptions.AUTO_SAVE_PREFIX);
        String lastTurnName = prefix + "-"
            + options.getText(ClientOptions.LAST_TURN_NAME)
            + FreeCol.FREECOL_SAVE_EXTENSION;
        String beforeLastTurnName = prefix + "-"
            + options.getText(ClientOptions.BEFORE_LAST_TURN_NAME)
            + FreeCol.FREECOL_SAVE_EXTENSION;
        File autoSaveDir = FreeColDirectories.getAutosaveDirectory();
        File lastTurnFile = new File(autoSaveDir, lastTurnName);
        File beforeLastTurnFile = new File(autoSaveDir, beforeLastTurnName);

        // if "last-turn" file exists, shift it to "before-last-turn" file
        if (lastTurnFile.exists()) {
            beforeLastTurnFile.delete();
            lastTurnFile.renameTo(beforeLastTurnFile);
        }
        saveGame(lastTurnFile);

        // conditional save after user-set period
        int saveGamePeriod = options.getInteger(ClientOptions.AUTOSAVE_PERIOD);
        int turnNumber = game.getTurn().getNumber();
        if (saveGamePeriod <= 1
            || (saveGamePeriod != 0 && turnNumber % saveGamePeriod == 0)) {
            Player player = freeColClient.getMyPlayer();
            String playerNation = (player == null) ? ""
                : Messages.message(player.getNation().getNameKey());
            String gid = Integer.toHexString(game.getUUID().hashCode());
            String name = prefix + "-" + gid  + "_" + playerNation
                + "_" + getSaveGameString(game.getTurn())
                + FreeCol.FREECOL_SAVE_EXTENSION;
            saveGame(new File(autoSaveDir, name));
        }
    }

    /**
     * Saves the game to the given file.
     *
     * @param file The <code>File</code>.
     * @return True if the game was saved.
     */
    private boolean saveGame(final File file) {
        FreeColServer server = freeColClient.getFreeColServer();
        boolean result = false;
        gui.showStatusPanel(Messages.message("status.savingGame"));
        try {
            server.setActiveUnit(gui.getActiveUnit());
            server.saveGame(file, freeColClient.getClientOptions());
            gui.closeStatusPanel();
            result = true;
        } catch (IOException e) {
            gui.showErrorMessage("couldNotSaveGame");
        }
        return result;
    }

    // Utilities for message handling.

    /**
     * Provides an opportunity to filter the messages delivered to the canvas.
     *
     * @param message the message that is candidate for delivery to the canvas
     * @return true if the message should be delivered
     */
    private boolean shouldAllowMessage(ModelMessage message) {
        BooleanOption option = freeColClient.getClientOptions()
            .getBooleanOption(message);
        return (option == null) ? true : option.getValue();
    }

    private synchronized void startIgnoringMessage(String key, int turn) {
        logger.finer("Ignoring model message with key " + key);
        messagesToIgnore.put(key, Integer.valueOf(turn));
    }

    private synchronized void stopIgnoringMessage(String key) {
        logger.finer("Removing model message with key " + key
            + " from ignored messages.");
        messagesToIgnore.remove(key);
    }

    private synchronized Integer getTurnForMessageIgnored(String key) {
        return messagesToIgnore.get(key);
    }

    /**
     * Displays the messages in the current turn report.
     */
    public void displayTurnReportMessages() {
        gui.showReportTurnPanel(turnReportMessages);
    }

    /**
     * Displays pending <code>ModelMessage</code>s.
     *
     * @param allMessages Display all messages or just the undisplayed ones.
     * @param endOfTurn Use a turn report panel if necessary.
     * @return True if any messages were displayed.
     */
    public boolean displayModelMessages(final boolean allMessages,
                                        final boolean endOfTurn) {
        Player player = freeColClient.getMyPlayer();
        int thisTurn = freeColClient.getGame().getTurn().getNumber();
        final ArrayList<ModelMessage> messages = new ArrayList<>();
        for (ModelMessage m : ((allMessages) ? player.getModelMessages()
                : player.getNewModelMessages())) {
            if (shouldAllowMessage(m)) {
                if (m.getMessageType() == MessageType.WAREHOUSE_CAPACITY) {
                    String key = m.getSourceId();
                    switch (m.getTemplateType()) {
                    case TEMPLATE:
                        for (String otherkey : m.getKeys()) {
                            if ("%goods%".equals(otherkey)) {
                                key += otherkey;
                                break;
                            }
                        }
                        break;
                    default:
                        break;
                    }

                    Integer turn = getTurnForMessageIgnored(key);
                    if (turn != null && turn.intValue() == thisTurn - 1) {
                        startIgnoringMessage(key, thisTurn);
                        m.setBeenDisplayed(true);
                        continue;
                    }
                }
                messages.add(m);
            }

            // flag all messages delivered as "beenDisplayed".
            m.setBeenDisplayed(true);
        }

        List<String> todo = new ArrayList<>();
        for (Entry<String, Integer> entry : messagesToIgnore.entrySet()) {
            if (entry.getValue().intValue() < thisTurn - 1) {
                todo.add(entry.getKey());
            }
        }
        while (!todo.isEmpty()) {
            String key = todo.remove(0);
            stopIgnoringMessage(key);
        }

        if (!messages.isEmpty()) {
            Runnable uiTask;
            if (endOfTurn) {
                turnReportMessages.addAll(messages);
                uiTask = new Runnable() {
                        public void run() {
                            displayTurnReportMessages();
                        }
                    };
            } else {
                uiTask = new Runnable() {
                        public void run() {
                            gui.showModelMessages(messages);
                        }
                    };
            }
            freeColClient.updateActions();
            if (SwingUtilities.isEventDispatchThread()) {
                uiTask.run();
            } else {
                try {
                    SwingUtilities.invokeAndWait(uiTask);
                } catch (InterruptedException e) {
                    logger.log(Level.WARNING, "Message display", e);
                } catch (InvocationTargetException e) {
                    logger.log(Level.WARNING, "Message display", e);
                }
            }
        }
        return !messages.isEmpty();
    }

    // Utilities to handle the transitions between the active-unit,
    // execute-orders and end-turn states.

    /**
     * Do the goto orders operation.
     *
     * @return True if all goto orders have been performed and no units
     *     reached their destination and are free to move again.
     */
    private boolean doExecuteGotoOrders() {
        if (gui.isShowingSubPanel()) return false; // Clear the panel first

        final Player player = freeColClient.getMyPlayer();
        final Unit active = gui.getActiveUnit();
        Unit stillActive = null;

        // Ensure the goto mode sticks.
        moveMode = moveMode.maximize(MoveMode.EXECUTE_GOTO_ORDERS);

        // Deal with the trade route units first.
        List<ModelMessage> messages = new ArrayList<>();
        while (player.hasNextTradeRouteUnit()) {
            Unit unit = player.getNextTradeRouteUnit();
            gui.setActiveUnit(unit);
            if (moveToDestination(unit, messages)) stillActive = unit;
        }
        if (!messages.isEmpty()) {
            for (ModelMessage m : messages) player.addModelMessage(m);
            nextModelMessage();
            gui.setActiveUnit((stillActive != null) ? stillActive : active);
            return false;
        }
        stillActive = null;

        // The active unit might also be a going-to unit.  Make sure it
        // gets processed first.  setNextGoingToUnit will fail harmlessly
        // if it is not a going-to unit so this is safe.
        if (active != null) player.setNextGoingToUnit(active);

        // Process all units.
        while (player.hasNextGoingToUnit()) {
            Unit unit = player.getNextGoingToUnit();
            gui.setActiveUnit(unit);

            // Move the unit as much as possible
            if (moveToDestination(unit, null)) stillActive = unit;

            // Give the player a chance to deal with any problems
            // shown in a popup before pressing on with more moves.
            if (gui.isShowingSubPanel()) {
                gui.requestFocusForSubPanel();
                stillActive = unit;
                break;
            }
        }
        gui.setActiveUnit((stillActive != null) ? stillActive : active);
        return stillActive == null;
    }

    /**
     * End the turn.
     *
     * @param showDialog Show the end turn dialog?
     * @return True if the turn ended.
     */
    private boolean doEndTurn(boolean showDialog) {
        if (showDialog) {
            List<Unit> units = new ArrayList<Unit>();
            for (Unit unit : freeColClient.getMyPlayer().getUnits()) {
                if (unit.couldMove()) units.add(unit);
            }
            if (!units.isEmpty()) {
                gui.showEndTurnDialog(units); // Modal dialog takes over
                return false;
            }
        }

        // Ensure end-turn mode sticks.
        moveMode = moveMode.maximize(MoveMode.END_TURN);

        // Make sure all goto orders are complete before ending turn.
        if (!doExecuteGotoOrders()) return false;

        // Check for desync as last thing!
        if (FreeColDebugger.isInDebugMode(FreeColDebugger.DebugMode.DESYNC)
            && DebugUtils.checkDesyncAction(freeColClient)) {
            freeColClient.getConnectController().reconnect();
            return false;
        }

        // Clean up lingering menus.
        gui.closeMenus();

        // Clear active unit if any.
        gui.setActiveUnit(null);

        // Unskip all skipped, some may have been faked in-client.
        // Server-side skipped units are set active in csNewTurn.
        for (Unit unit : freeColClient.getMyPlayer().getUnits()) {
            if (unit.getState() == UnitState.SKIPPED) {
                unit.setState(UnitState.ACTIVE);
            }
        }

        // Restart the selection cycle.
        moveMode = MoveMode.NEXT_ACTIVE_UNIT;

        // Clear outdated turn report messages.
        turnReportMessages.clear();

        // Inform the server of end of turn.
        return askServer().endTurn();
    }

    /**
     * Makes a new unit active if any, or focus on a tile (useful if the
     * current unit just died).
     *
     * Displays any new <code>ModelMessage</code>s with
     * {@link #nextModelMessage}.
     *
     * @param tile The <code>Tile</code> to select if no new unit can
     *     be made active.
     */
    private void nextActiveUnit(Tile tile) {
        // Always flush outstanding messages first.
        nextModelMessage();

        // Flush any outstanding orders once the mode is raised.
        if (moveMode != MoveMode.NEXT_ACTIVE_UNIT
            && !doExecuteGotoOrders()) {
            return;
        }

        // Look for active units.
        Player player = freeColClient.getMyPlayer();
        Unit unit = gui.getActiveUnit();
        if (unit != null && !unit.isDisposed()
            && unit.couldMove()) return; // Current active unit has more to do.
        if (player.hasNextActiveUnit()) {
            gui.setActiveUnit(player.getNextActiveUnit());
            return; // Successfully found a unit to display
        }
        gui.setActiveUnit(null);

        // No active units left.  Do the goto orders.
        if (!doExecuteGotoOrders()) return;

        // If not already ending the turn, use the fallback tile if
        // supplied, then check for automatic end of turn, otherwise
        // just select nothing and wait.
        ClientOptions options = freeColClient.getClientOptions();
        if (tile != null) {
            gui.setSelectedTile(tile, false);
        } else if (options.getBoolean(ClientOptions.AUTO_END_TURN)) {
            doEndTurn(options.getBoolean(ClientOptions.SHOW_END_TURN_DIALOG));
        }
    }


    // Movement support.

    /**
     * Moves the given unit towards its destination/s if possible.
     *
     * @param unit The <code>Unit</code> to move.
     * @param messages An optional list in which to retain any
     *     trade route <code>ModelMessage</code>s generated.
     * @return True if the unit reached its destination and has more moves
     *     to make.
     */
    private boolean moveToDestination(Unit unit, List<ModelMessage> messages) {
        Location destination;
        if (!requireOurTurn()
            || unit.isAtSea()
            || unit.getMovesLeft() <= 0
            || unit.getState() == UnitState.SKIPPED) {
            return false;
        } else if (unit.getTradeRoute() != null) {
            return followTradeRoute(unit, messages);
        } else if ((destination = unit.getDestination()) == null) {
            return unit.getMovesLeft() > 0;
        }

        // Find a path to the destination and try to follow it.
        final Player player = freeColClient.getMyPlayer();
        PathNode path = unit.findPath(destination);
        if (path == null) {
            StringTemplate src = unit.getLocation()
                .getLocationNameFor(player);
            StringTemplate dst = destination.getLocationNameFor(player);
            StringTemplate template = StringTemplate
                .template("selectDestination.failed")
                .addStringTemplate("%unit%",
                    unit.getLabel(Unit.UnitLabelType.NATIONAL))
                .addStringTemplate("%location%", src)
                .addStringTemplate("%destination%", dst);
            gui.showInformationMessage(unit, template);
            return false;
        }
        gui.setActiveUnit(unit);

        // Clear ordinary destinations if arrived.
        if (movePath(unit, path) && unit.isAtLocation(destination)) {
            clearGotoOrders(unit);
            // Check cash-in, and if the unit has moves left and was
            // not set to SKIPPED by moveDirection, then return true
            // to show that this unit could continue.
            if (!checkCashInTreasureTrain(unit)
                && unit.getMovesLeft() > 0
                && unit.getState() != UnitState.SKIPPED) {
                return true;
            }
        }
        return false;
    }

    /**
     * Move a unit in a given direction.
     *
     * Public for the test suite.
     *
     * @param unit The <code>Unit</code> to move.
     * @param direction The <code>Direction</code> to move in.
     * @param interactive Interactive mode: play sounds and emit errors.
     * @return True if the unit can possibly move further.
     */
    public boolean moveDirection(Unit unit, Direction direction,
                                 boolean interactive) {
        // If this move would reach the unit destination but we
        // discover that it would be permanently impossible to complete,
        // clear the destination.
        Unit.MoveType mt = unit.getMoveType(direction);
        Location destination = unit.getDestination();
        boolean clearDestination = destination != null
            && unit.hasTile()
            && Map.isSameLocation(unit.getTile().getNeighbourOrNull(direction),
                                  destination);

        // Consider all the move types.
        boolean result = mt.isLegal();
        switch (mt) {
        case MOVE_HIGH_SEAS:
            if (freeColClient.getMyPlayer().getEurope() == null) {
                ; // do nothing
            } else if (destination == null) {
                result = moveHighSeas(unit, direction);
                break;
            } else if (destination instanceof Europe) {
                result = moveTo(unit, destination);
                break;
            }
            // Fall through
        case MOVE:
            result = moveMove(unit, direction);
            break;
        case EXPLORE_LOST_CITY_RUMOUR:
            result = moveExplore(unit, direction);
            break;
        case ATTACK_UNIT:
            result = moveAttack(unit, direction);
            break;
        case ATTACK_SETTLEMENT:
            result = moveAttackSettlement(unit, direction);
            break;
        case EMBARK:
            result = moveEmbark(unit, direction);
            break;
        case ENTER_INDIAN_SETTLEMENT_WITH_FREE_COLONIST:
            result = moveLearnSkill(unit, direction);
            break;
        case ENTER_INDIAN_SETTLEMENT_WITH_SCOUT:
            result = moveScoutIndianSettlement(unit, direction);
            break;
        case ENTER_INDIAN_SETTLEMENT_WITH_MISSIONARY:
            result = moveUseMissionary(unit, direction);
            break;
        case ENTER_FOREIGN_COLONY_WITH_SCOUT:
            result = moveScoutColony(unit, direction);
            break;
        case ENTER_SETTLEMENT_WITH_CARRIER_AND_GOODS:
            result = moveTrade(unit, direction);
            break;

        // Illegal moves
        case MOVE_NO_ACCESS_BEACHED:
            if (interactive || clearDestination) {
                gui.playSound("sound.event.illegalMove");
                StringTemplate nation = getNationAt(unit.getTile(), direction);
                gui.showInformationMessage(unit,
                    StringTemplate.template("move.noAccessBeached")
                    .addStringTemplate("%nation%", nation));
            }
            break;
        case MOVE_NO_ACCESS_CONTACT:
            if (interactive || clearDestination) {
                gui.playSound("sound.event.illegalMove");
                StringTemplate nation = getNationAt(unit.getTile(), direction);
                gui.showInformationMessage(unit,
                    StringTemplate.template("move.noAccessContact")
                    .addStringTemplate("%nation%", nation));
            }
            break;
        case MOVE_NO_ACCESS_GOODS:
            if (interactive || clearDestination) {
                gui.playSound("sound.event.illegalMove");
                StringTemplate nation = getNationAt(unit.getTile(), direction);
                gui.showInformationMessage(unit,
                    StringTemplate.template("move.noAccessGoods")
                    .addStringTemplate("%nation%", nation)
                    .addStringTemplate("%unit%",
                        unit.getLabel(Unit.UnitLabelType.NATIONAL)));
            }
            break;
        case MOVE_NO_ACCESS_LAND:
            if (!moveDisembark(unit, direction)) {
                if (interactive) {
                    gui.playSound("sound.event.illegalMove");
                }
            }
            break;
        case MOVE_NO_ACCESS_MISSION_BAN:
            if (interactive || clearDestination) {
                gui.playSound("sound.event.illegalMove");
                StringTemplate nation = getNationAt(unit.getTile(), direction);
                gui.showInformationMessage(unit,
                    StringTemplate.template("move.noAccessMissionBan")
                    .addStringTemplate("%unit%",
                        unit.getLabel(Unit.UnitLabelType.NATIONAL))
                    .addStringTemplate("%nation%", nation));
            }
            break;
        case MOVE_NO_ACCESS_SETTLEMENT:
            if (interactive || clearDestination) {
                gui.playSound("sound.event.illegalMove");
                StringTemplate nation = getNationAt(unit.getTile(), direction);
                gui.showInformationMessage(unit,
                    StringTemplate.template("move.noAccessSettlement")
                    .addStringTemplate("%unit%",
                        unit.getLabel(Unit.UnitLabelType.NATIONAL))
                    .addStringTemplate("%nation%", nation));
            }
            break;
        case MOVE_NO_ACCESS_SKILL:
            if (interactive || clearDestination) {
                gui.playSound("sound.event.illegalMove");
                gui.showInformationMessage(unit,
                    StringTemplate.template("move.noAccessSkill")
                    .addStringTemplate("%unit%",
                        unit.getLabel(Unit.UnitLabelType.NATIONAL)));
            }
            break;
        case MOVE_NO_ACCESS_TRADE:
            if (interactive || clearDestination) {
                gui.playSound("sound.event.illegalMove");
                StringTemplate nation = getNationAt(unit.getTile(), direction);
                gui.showInformationMessage(unit,
                    StringTemplate.template("move.noAccessTrade")
                    .addStringTemplate("%nation%", nation));
            }
            break;
        case MOVE_NO_ACCESS_WAR:
            if (interactive || clearDestination) {
                gui.playSound("sound.event.illegalMove");
                StringTemplate nation = getNationAt(unit.getTile(), direction);
                gui.showInformationMessage(unit,
                    StringTemplate.template("move.noAccessWar")
                    .addStringTemplate("%nation%", nation));
            }
            break;
        case MOVE_NO_ACCESS_WATER:
            if (interactive || clearDestination) {
                gui.playSound("sound.event.illegalMove");
                gui.showInformationMessage(unit,
                    StringTemplate.template("move.noAccessWater")
                    .addStringTemplate("%unit%",
                        unit.getLabel(Unit.UnitLabelType.NATIONAL)));
            }
            break;
        case MOVE_NO_ATTACK_MARINE:
            if (interactive || clearDestination) {
                gui.playSound("sound.event.illegalMove");
                gui.showInformationMessage(unit,
                    StringTemplate.template("move.noAttackWater")
                    .addStringTemplate("%unit%",
                        unit.getLabel(Unit.UnitLabelType.NATIONAL)));
            }
            break;
        case MOVE_NO_MOVES:
            // The unit may have some moves left, but not enough
            // to move to the next node.  The move is illegal
            // this turn, but might not be next turn, so do not cancel the
            // destination but set the state to skipped instead.
            clearDestination = false;
            unit.setState(UnitState.SKIPPED);
            break;
        case MOVE_NO_TILE:
            if (interactive || clearDestination) {
                gui.playSound("sound.event.illegalMove");
                gui.showInformationMessage(unit,
                    StringTemplate.template("move.noTile")
                    .addStringTemplate("%unit%",
                        unit.getLabel(Unit.UnitLabelType.NATIONAL)));
            }
            break;
        default:
            if (interactive || clearDestination) {
                gui.playSound("sound.event.illegalMove");
            }
            result = false;
            break;
        }
        if (clearDestination && !unit.isDisposed()) clearGotoOrders(unit);
        return result;
    }

    /**
     * Follow a path.
     *
     * @param unit The <code>Unit</code> to move.
     * @param path The path to follow.
     * @return True if the unit has completed the path and can move further.
     */
    private boolean movePath(Unit unit, PathNode path) {
        // Traverse the path to the destination.
        for (; path != null; path = path.next) {
            if (unit.isAtLocation(path.getLocation())) continue;

            if (path.getLocation() instanceof Europe) {
                if (unit.hasTile()
                    && unit.getTile().isDirectlyHighSeasConnected()) {
                    moveTo(unit, path.getLocation());
                } else {
                    logger.warning("Can not move to Europe from "
                        + unit.getLocation()
                        + " on path: " + path.fullPathToString());
                }
                return false;
            } else if (path.getLocation() instanceof Tile) {
                if (path.getDirection() == null) {
                    if (unit.isInEurope()) {
                        moveTo(unit, unit.getGame().getMap());
                    } else {
                        logger.warning("Null direction on path: "
                            + path.fullPathToString());
                    }
                    return false;
                } else {
                    if (!moveDirection(unit, path.getDirection(), false)) {
                        return false;
                    }
                    if (unit.hasTile()
                        && unit.getTile().getDiscoverableRegion() != null) {
                        // Break up the goto to allow region naming to occur,
                        // BR#2707
                        return false;
                    }
                }
            } else {
                logger.warning("Bad path: " + path.fullPathToString());
            }
        }
        return true;
    }

    /**
     * Confirm attack or demand a tribute from a native settlement, following
     * an attacking move.
     *
     * @param unit The <code>Unit</code> to perform the attack.
     * @param direction The direction in which to attack.
     * @return True if the unit could move further.
     */
    private boolean moveAttack(Unit unit, Direction direction) {
        clearGotoOrders(unit);

        Tile tile = unit.getTile();
        Tile target = tile.getNeighbourOrNull(direction);
        Unit u = target.getFirstUnit();
        if (u == null) {
            throw new IllegalStateException("Attacking empty tile!");
        } else if (unit.getOwner().owns(u)) {
            throw new IllegalStateException("Attacking own unit!");
        }
        if (gui.confirmHostileAction(unit, target)
            && gui.confirmPreCombat(unit, target)) {
            askServer().attack(unit, direction);
            nextActiveUnit();
        }
        // Always return false, as the unit has either attacked and lost
        // its remaining moves, or the move can not proceed because it is
        // blocked.
        return false;
    }

    /**
     * Confirm attack or demand a tribute from a settlement, following
     * an attacking move.
     *
     * @param unit The <code>Unit</code> to perform the attack.
     * @param direction The direction in which to attack.
     * @return True if the unit could move further.
     */
    private boolean moveAttackSettlement(Unit unit, Direction direction) {
        Tile tile = unit.getTile();
        Tile target = tile.getNeighbourOrNull(direction);
        Settlement settlement = target.getSettlement();
        if (settlement == null) {
            throw new IllegalStateException("Attacking empty tile!");
        } else if (unit.getOwner().owns(settlement)) {
            throw new IllegalStateException("Attacking own settlement!");
        }
        GUI.ArmedUnitSettlementAction act
            = gui.getArmedUnitSettlementChoice(settlement);
        if (act == null) return true; // Cancelled
        switch (act) {
        case SETTLEMENT_ATTACK:
            if (gui.confirmHostileAction(unit, target)
                && gui.confirmPreCombat(unit, target)) {
                askServer().attack(unit, direction);
                nextActiveUnit();
                Colony col = target.getColony();
                if (col != null && unit.getOwner().owns(col)) {
                    gui.showColonyPanel(col, unit);
                }
                return false;
            }
            break;
        case SETTLEMENT_TRIBUTE:
            int amount = (settlement instanceof Colony)
                ? gui.confirmEuropeanTribute(unit, (Colony)settlement,
                    getNationSummary(settlement.getOwner()))
                : (settlement instanceof IndianSettlement)
                ? gui.confirmNativeTribute(unit, (IndianSettlement)settlement)
                : -1;
            if (amount <= 0) return true; // Cancelled
            return moveTribute(unit, amount, direction);

        default:
            logger.warning("showArmedUnitSettlementDialog fail: " + act);
            break;
        }
        return true;
    }

    /**
     * Initiates diplomacy with a foreign power.
     *
     * @param unit The <code>Unit</code> negotiating.
     * @param direction The direction of a settlement to negotiate with.
     * @param dt The base <code>DiplomaticTrade</code> agreement to
     *     begin the negotiation with.
     * @return True if the unit can move further.
     */
    private boolean moveDiplomacy(Unit unit, Direction direction,
                                  DiplomaticTrade dt) {
        Settlement settlement = getSettlementAt(unit.getTile(), direction);
        if (settlement == null) return false;
        if (!(settlement instanceof Colony)) return false;
        Colony colony = (Colony)settlement;

        // Can not negotiate with the REF.
        final Game game = freeColClient.getGame();
        final Player player = unit.getOwner();
        final Player other = colony.getOwner();
        if (other == player.getREFPlayer()) {
            throw new IllegalStateException("Unit tried to negotiate with REF");
        }

        StringTemplate nation = other.getNationName();
        ModelMessage m = null;
        TradeStatus status;
        while (dt != null) {
            // Inform server of current agreement, exit if it did not
            // require a response (i.e. was not a proposal).
            status = dt.getStatus();
            dt = askServer().diplomacy(game, unit, colony, dt);
            if (status != TradeStatus.PROPOSE_TRADE) break;
            
            // Process the result of a proposal.
            status = (dt == null) ? TradeStatus.REJECT_TRADE : dt.getStatus();
            m = null;
            switch (status) {
            case PROPOSE_TRADE:
                break;
            case ACCEPT_TRADE:
                m = new ModelMessage(MessageType.FOREIGN_DIPLOMACY,
                                     "negotiationDialog.offerAccepted",
                                     colony)
                    .addStringTemplate("%nation%", nation);
                dt = null;
                break;
            case REJECT_TRADE:
                m = new ModelMessage(MessageType.FOREIGN_DIPLOMACY,
                                     "negotiationDialog.offerRejected",
                                     colony)
                    .addStringTemplate("%nation%", nation);
                dt = null;
                break;
            default:
                throw new IllegalStateException("Bogus trade status" + status);
            }
            if (m != null) player.addModelMessage(m);

            // If it was a counter proposal, consider it.
            if (dt != null) {
                dt = gui.showDiplomaticTradeDialog(unit, colony, dt,
                    dt.getSendMessage(player, colony));
            }
        }
        updateAfterMove();
        nextActiveUnit();
        return false;
    }

    /**
     * Check the carrier for passengers to disembark, possibly
     * snatching a useful result from the jaws of a
     * MOVE_NO_ACCESS_LAND failure.
     *
     * @param unit The carrier containing the unit to disembark.
     * @param direction The direction in which to disembark the unit.
     * @return True if the disembark "succeeds" (which deliberately includes
     *     declined disembarks).
     */
    private boolean moveDisembark(Unit unit, final Direction direction) {
        Tile tile = unit.getTile().getNeighbourOrNull(direction);
        if (tile.getFirstUnit() != null
            && tile.getFirstUnit().getOwner() != unit.getOwner()) {
            return false; // Can not disembark onto other nation units.
        }

        // Disembark selected units able to move.
        final List<Unit> disembarkable = new ArrayList<>();
        unit.setStateToAllChildren(UnitState.ACTIVE);
        for (Unit u : unit.getUnitList()) {
            if (u.getMoveType(tile).isProgress()) {
                disembarkable.add(u);
            }
        }
        if (disembarkable.isEmpty()) {
            // Did not find any unit that could disembark, fail.
            return false;
        }

        while (!disembarkable.isEmpty()) {
            if (disembarkable.size() == 1) {
                if (gui.confirm(true, tile,
                                StringTemplate.key("disembark.text"),
                                disembarkable.get(0), "ok", "cancel")) {
                    moveDirection(disembarkable.get(0), direction, false);
                }
                break;
            }
            List<ChoiceItem<Unit>> choices = new ArrayList<>();
            for (Unit dUnit : disembarkable) {
                choices.add(new ChoiceItem<Unit>(dUnit.getDescription(Unit.UnitLabelType.NATIONAL),
                        dUnit));
            }
            if (disembarkable.size() > 1) {
                choices.add(new ChoiceItem<Unit>(Messages.message("all"), unit));
            }

            // Use moveDirection() to disembark units as while the
            // destination tile is known to be clear of other player
            // units or settlements, it may have a rumour or need
            // other special handling.
            Unit u = gui.getChoice(true, unit.getTile(),
                                   Messages.message("disembark.text"), unit,
                                   "disembark.cancel", choices);
            if (u == null) { // Cancelled, done.
                break;
            } else if (u == unit) { // Disembark all.
                for (Unit dUnit : disembarkable) {
                    // Guard against loss of control when asking the
                    // server to move the unit.
                    try {
                        moveDirection(dUnit, direction, false);
                    } finally {
                        continue;
                    }
                }
                return true;
            }
            moveDirection(u, direction, false);
            disembarkable.remove(u);
        }
        return true;
    }

    /**
     * Embarks the specified unit onto a carrier in a specified direction
     * following a move of MoveType.EMBARK.
     *
     * @param unit The <code>Unit</code> that wishes to embark.
     * @param direction The direction in which to embark.
     * @return True if the unit could move further.
     */
    private boolean moveEmbark(Unit unit, Direction direction) {
        if (unit.getColony() != null
            && !gui.confirmLeaveColony(unit)) return false;

        Tile sourceTile = unit.getTile();
        Tile destinationTile = sourceTile.getNeighbourOrNull(direction);
        Unit carrier = null;
        List<ChoiceItem<Unit>> choices = new ArrayList<>();
        for (Unit u : destinationTile.getUnitList()) {
            if (u.canAdd(unit)) {
                String m = u.getDescription(Unit.UnitLabelType.NATIONAL);
                choices.add(new ChoiceItem<Unit>(m, u));
                carrier = u; // Save a default
            }
        }
        if (choices.isEmpty()) {
            throw new RuntimeException("Unit " + unit.getId()
                + " found no carrier to embark upon.");
        } else if (choices.size() == 1) {
            // Use the default
        } else {
            carrier = gui.getChoice(true, unit.getTile(),
                                    Messages.message("embark.text"), unit,
                                    "embark.cancel", choices);
            if (carrier == null) return true; // User cancelled
        }

        // Proceed to embark
        clearGotoOrders(unit);
        if (!askServer().embark(unit, carrier, direction)
            || unit.getLocation() != carrier) {
            unit.setState(UnitState.SKIPPED);
            return false;
        }
        unit.getOwner().invalidateCanSeeTiles();
        if (carrier.getMovesLeft() > 0) {
            gui.setActiveUnit(carrier);
        } else {
            nextActiveUnit();
        }
        return false;
    }

    /**
     * Confirm exploration of a lost city rumour, following a move of
     * MoveType.EXPLORE_LOST_CITY_RUMOUR.
     *
     * @param unit The <code>Unit</code> that is exploring.
     * @param direction The direction of a rumour.
     * @return True if the unit can move further.
     */
    private boolean moveExplore(Unit unit, Direction direction) {
        Tile tile = unit.getTile().getNeighbourOrNull(direction);
        if (!gui.confirm(true, unit.getTile(),
                StringTemplate.key("exploreLostCityRumour.text"), unit,
                "exploreLostCityRumour.yes", "exploreLostCityRumour.no")) {
            return true;
        }
        if (tile.getLostCityRumour().getType()== LostCityRumour.RumourType.MOUNDS
            && !gui.confirm(true, unit.getTile(),
                StringTemplate.key("exploreMoundsRumour.text"), unit,
                "exploreLostCityRumour.yes", "exploreLostCityRumour.no")) {
            askServer().declineMounds(unit, direction);
        }
        return moveMove(unit, direction);
    }

    /**
     * Moves a unit onto the "high seas" in a specified direction following
     * a move of MoveType.MOVE_HIGH_SEAS.
     * This may result in a move to Europe, no move, or an ordinary move.
     *
     * @param unit The <code>Unit</code> to be moved.
     * @param direction The direction in which to move.
     * @return True if the unit can move further.
     */
    private boolean moveHighSeas(Unit unit, Direction direction) {
        // Confirm moving to Europe if told to move to a null tile
        // (FIXME: can this still happen?), or if crossing the boundary
        // between coastal and high sea.  Otherwise just move.
        Tile oldTile = unit.getTile();
        Tile newTile = oldTile.getNeighbourOrNull(direction);
        if (newTile == null
            || (!oldTile.isDirectlyHighSeasConnected()
                && newTile.isDirectlyHighSeasConnected())) {
            if (unit.getTradeRoute() != null) {
                TradeRouteStop stop = unit.getStop();
                if (stop != null && TradeRoute.isStopValid(unit, stop)
                    && stop.getLocation() instanceof Europe) {
                    moveTo(unit, stop.getLocation());
                    return false;
                }
            } else if (unit.getDestination() instanceof Europe) {
                moveTo(unit, unit.getDestination());
                return false;
            } else {
                if (gui.confirm(true, oldTile,
                        StringTemplate.template("highseas.text")
                            .addAmount("%number%", unit.getSailTurns()),
                        unit, "highseas.yes", "highseas.no")) {
                    moveTo(unit, unit.getOwner().getEurope());
                    return false;
                }
            }
        }
        return moveMove(unit, direction);
    }

    /**
     * Move a free colonist to a native settlement to learn a skill following
     * a move of MoveType.ENTER_INDIAN_SETTLEMENT_WITH_FREE_COLONIST.
     * The colonist does not physically get into the village, it will
     * just stay where it is and gain the skill.
     *
     * @param unit The <code>Unit</code> to learn the skill.
     * @param direction The direction in which the Indian settlement lies.
     * @return True if the unit can move further.
     */
    private boolean moveLearnSkill(Unit unit, Direction direction) {
        clearGotoOrders(unit);
        // Refresh knowledge of settlement skill.  It may have been
        // learned by another player.
        if (!askServer().askSkill(unit, direction)) return false;

        IndianSettlement settlement
            = (IndianSettlement)getSettlementAt(unit.getTile(), direction);
        UnitType skill = settlement.getLearnableSkill();
        if (skill == null) {
            gui.showInformationMessage(settlement,
                                       "indianSettlement.noMoreSkill");
        } else if (!unit.getType().canBeUpgraded(skill, ChangeType.NATIVES)) {
            gui.showInformationMessage(settlement,
                StringTemplate.template("indianSettlement.cantLearnSkill")
                    .addStringTemplate("%unit%",
                        unit.getLabel(Unit.UnitLabelType.NATIONAL))
                    .add("%skill%", skill.getNameKey()));
        } else if (gui.confirm(true, unit.getTile(),
                StringTemplate.template("learnSkill.text")
                    .add("%skill%", skill.getNameKey()),
                unit, "learnSkill.yes", "learnSkill.no")) {
            if (askServer().learnSkill(unit, direction)) {
                if (unit.isDisposed()) {
                    gui.showInformationMessage(settlement, "learnSkill.die");
                    nextActiveUnit(unit.getTile());
                    return false;
                }
                if (unit.getType() != skill) {
                    gui.showInformationMessage(settlement, "learnSkill.leave");
                }
            }
        }
        nextActiveUnit();
        return false;
    }

    /**
     * Actually move a unit in a specified direction, following a move
     * of MoveType.MOVE.
     *
     * @param unit The <code>Unit</code> to be moved.
     * @param direction The direction in which to move the Unit.
     * @return True if the unit can move further.
     */
    private boolean moveMove(Unit unit, Direction direction) {
        // If we are in a colony, or Europe, load sentries.
        if (unit.canCarryUnits() && unit.hasSpaceLeft()
            && (unit.getColony() != null || unit.isInEurope())) {
            boolean boarded = false;
            for (Unit sentry : unit.getLocation().getUnitList()) {
                if (sentry.getState() == UnitState.SENTRY) {
                    if (unit.canAdd(sentry)) {
                        boarded |= boardShip(sentry, unit);
                        logger.finest("Unit " + unit
                            + " loaded sentry " + sentry);
                    } else {
                        logger.finest("Unit " + sentry
                            + " is too big to board " + unit);
                    }
                }
            }
            // Boarding consumed this unit's moves.
            if (boarded && unit.getMovesLeft() <= 0) return false;
        }

        // Ask the server
        UnitWas unitWas = new UnitWas(unit);
        if (!askServer().move(unit, direction)) {
            // Can fail due to desynchronization.  Skip this unit so
            // we do not end up retrying indefinitely.
            unit.setState(UnitState.SKIPPED);
            return false;
        }

        unit.getOwner().invalidateCanSeeTiles();
        unitWas.fireChanges();
        
        final Tile tile = unit.getTile();

        // Perform a short pause on an active unit's last move if
        // the option is enabled.
        ClientOptions options = freeColClient.getClientOptions();
        if (unit.getMovesLeft() <= 0
            && options.getBoolean(ClientOptions.UNIT_LAST_MOVE_DELAY)) {
            gui.paintImmediatelyCanvasInItsBounds();
            try {
                Thread.sleep(UNIT_LAST_MOVE_DELAY);
            } catch (InterruptedException e) {} // Ignore
        }

        // Update the active unit and GUI.
        if (unit.isDisposed() || checkCashInTreasureTrain(unit)) return false;
        if (tile.getColony() != null
            && unit.isCarrier()
            && unit.getTradeRoute() == null
            && (unit.getDestination() == null
                || unit.getDestination().getTile() == tile.getTile())) {
            gui.showColonyPanel(tile.getColony(), unit);
        }
        if (unit.getMovesLeft() <= 0) return false;
        displayModelMessages(false);
        if (!gui.onScreen(tile)) gui.setSelectedTile(tile, false);
        return true;
    }

    /**
     * Move to a foreign colony and either attack, negotiate with the
     * foreign power or spy on them.  Follows a move of
     * MoveType.ENTER_FOREIGN_COLONY_WITH_SCOUT.
     *
     * FIXME: Unify trade and negotiation.
     *
     * @param unit The unit that will spy, negotiate or attack.
     * @param direction The direction in which the foreign colony lies.
     * @return True if the unit can move further.
     */
    private boolean moveScoutColony(Unit unit, Direction direction) {
        final Game game = freeColClient.getGame();
        Colony colony = (Colony) getSettlementAt(unit.getTile(), direction);
        boolean canNeg = colony.getOwner() != unit.getOwner().getREFPlayer();
        clearGotoOrders(unit);

        GUI.ScoutColonyAction act
            = gui.getScoutForeignColonyChoice(colony, unit, canNeg);
        if (act == null) return true; // Cancelled
        switch (act) {
        case FOREIGN_COLONY_ATTACK:
            return moveAttackSettlement(unit, direction);
        case FOREIGN_COLONY_NEGOTIATE:
            Player player = unit.getOwner();
            DiplomaticTrade agreement
                = new DiplomaticTrade(game, TradeContext.DIPLOMATIC,
                                      player, colony.getOwner(), null, 0);
            agreement = gui.showDiplomaticTradeDialog(unit, colony,
                agreement, agreement.getSendMessage(player, colony));
            return (agreement == null
                || agreement.getStatus() == TradeStatus.REJECT_TRADE) ? true
                : moveDiplomacy(unit, direction, agreement);
        case FOREIGN_COLONY_SPY:
            return moveSpy(unit, direction);
        default:
            logger.warning("showScoutForeignColonyDialog fail: " + act);
            break;
        }
        return true;
    }

    /**
     * Move a scout into an Indian settlement to speak with the chief,
     * or demand a tribute following a move of
     * MoveType.ENTER_INDIAN_SETTLEMENT_WITH_SCOUT.
     * The scout does not physically get into the village, it will
     * just stay where it is.
     *
     * @param unit The <code>Unit</code> that is scouting.
     * @param direction The direction in which the Indian settlement lies.
     * @return True if the unit can move further.
     */
    private boolean moveScoutIndianSettlement(Unit unit, Direction direction) {
        Tile unitTile = unit.getTile();
        Tile tile = unitTile.getNeighbourOrNull(direction);
        IndianSettlement settlement = tile.getIndianSettlement();
        Player player = unit.getOwner();
        clearGotoOrders(unit);

        // Offer the choices.
        String number = askServer().scoutSettlement(unit, direction);
        if (number == null) number = Messages.message("many");
        GUI.ScoutIndianSettlementAction act
            = gui.getScoutIndianSettlementChoice(settlement, number);
        if (act == null) return true; // Cancelled
        switch (act) {
        case INDIAN_SETTLEMENT_ATTACK:
            if (!gui.confirmPreCombat(unit, tile)) return true;
            askServer().attack(unit, direction);
            return false;
        case INDIAN_SETTLEMENT_SPEAK:
            final int oldGold = player.getGold();
            String result = askServer().scoutSpeakToChief(unit, direction);
            if (result == null) {
                return false; // Fail
            } else if ("die".equals(result)) {
                gui.showInformationMessage(settlement,
                    "scoutSettlement.speakDie");
                nextActiveUnit(unitTile);
                return false;
            } else if ("expert".equals(result)) {
                gui.showInformationMessage(settlement,
                    StringTemplate.template("scoutSettlement.expertScout")
                    .add("%unit%", unit.getType().getNameKey()));
            } else if ("tales".equals(result)) {
                gui.showInformationMessage(settlement,
                    "scoutSettlement.speakTales");
            } else if ("beads".equals(result)) {
                gui.showInformationMessage(settlement,
                    StringTemplate.template("scoutSettlement.speakBeads")
                    .addAmount("%amount%", player.getGold() - oldGold));
            } else if ("nothing".equals(result)) {
                gui.showInformationMessage(settlement,
                    StringTemplate.template("scoutSettlement.speakNothing")
                    .addStringTemplate("%nation%", player.getNationName()));
            } else {
                logger.warning("Invalid result from askScoutSpeak: " + result);
            }
            updateAfterMove();
            nextActiveUnit();
            return false;
        case INDIAN_SETTLEMENT_TRIBUTE:
            return moveTribute(unit, 1, direction);
        default:
            logger.warning("showScoutIndianSettlementDialog fail: " + act);
            break;
        }
        return true;
    }

    /**
     * Spy on a foreign colony.
     *
     * @param unit The <code>Unit</code> that is spying.
     * @param direction The <code>Direction</code> of a colony to spy on.
     * @return True if the unit can move further.
     */
    private boolean moveSpy(Unit unit, Direction direction) {
        UnitWas unitWas = new UnitWas(unit);
        if (askServer().spy(unit, direction)) {
            unitWas.fireChanges();
            nextActiveUnit();
        }
        return false;
    }

    /**
     * Arrive at a settlement with a laden carrier following a move of
     * MoveType.ENTER_SETTLEMENT_WITH_CARRIER_AND_GOODS.
     *
     * @param unit The carrier.
     * @param direction The direction to the settlement.
     * @return True if the unit can move further.
     */
    private boolean moveTrade(Unit unit, Direction direction) {
        clearGotoOrders(unit);

        Settlement settlement = getSettlementAt(unit.getTile(), direction);
        if (settlement instanceof Colony) {
            final Game game = freeColClient.getGame();
            final Player player = unit.getOwner();
            DiplomaticTrade agreement
                = new DiplomaticTrade(game, TradeContext.TRADE,
                    player, settlement.getOwner(), null, 0);
            agreement = gui.showDiplomaticTradeDialog(unit, settlement,
                agreement, agreement.getSendMessage(player, settlement));
            return (agreement == null
                || agreement.getStatus() == TradeStatus.REJECT_TRADE) ? true
                : moveDiplomacy(unit, direction, agreement);
        } else if (settlement instanceof IndianSettlement) {
            return moveTradeIndianSettlement(unit, direction);
        } else {
            throw new IllegalStateException("Bogus settlement: "
                + settlement.getId());
        }
    }

    /**
     * Trading with the natives, including buying, selling and
     * delivering gifts.  (Deliberate use of Settlement rather than
     * IndianSettlement throughout these routines as some unification
     * with colony trading is anticipated, and the native AI already
     * uses the same DeliverGiftMessage to deliver gifts to Colonies).
     *
     * @param unit The <code>Unit</code> that is a carrier containing goods.
     * @param direction The direction the unit could move in order to enter a
     *            <code>Settlement</code>.
     * @exception IllegalArgumentException if the unit is not a carrier, or if
     *                there is no <code>Settlement</code> in the given
     *                direction.
     * @see Settlement
     * @return True if the unit can move further.
     */
    private boolean moveTradeIndianSettlement(Unit unit, Direction direction) {
        Settlement settlement = getSettlementAt(unit.getTile(), direction);

        StringTemplate baseTemplate
            = StringTemplate.template("tradeProposition.welcome")
                .addStringTemplate("%nation%",
                    settlement.getOwner().getNationName())
                .addName("%settlement%", settlement.getName());
        StringTemplate template = baseTemplate;
        UnitWas unitWas = new UnitWas(unit);
        boolean[] results = askServer()
            .openTransactionSession(unit, settlement);
        while (results != null) {
            // The session tracks buy/sell/gift events and disables
            // them when one happens.  So only offer such options if
            // the session allows it and the carrier is in good shape.
            boolean buy = results[0] && unit.hasSpaceLeft();
            boolean sel = results[1] && unit.hasGoodsCargo();
            boolean gif = results[2] && unit.hasGoodsCargo();
            if (!buy && !sel && !gif) break;

            GUI.TradeAction act = gui.getIndianSettlementTradeChoice(settlement,
                template, buy, sel, gif);
            if (act == null) break;
            StringTemplate t = null;
            switch (act) {
            case BUY:
                t = attemptBuyFromSettlement(unit, settlement);
                if (t == null) {
                    results[0] = false;
                    template = baseTemplate;
                } else {
                    template = t;
                }
                break;
            case SELL:
                t = attemptSellToSettlement(unit, settlement);
                if (t == null) {
                    results[1] = false;
                    template = baseTemplate;
                } else {
                    template = t;
                }
                break;
            case GIFT:
                t = attemptGiftToSettlement(unit, settlement);
                if (t == null) {
                    results[2] = false;
                    template = baseTemplate;
                } else {
                    template = t;
                }
                break;
            default:
                logger.warning("showIndianSettlementTradeDialog fail: "
                    + act);
                results = null;
                break;
            }
            if (template == abortTrade) template = baseTemplate;
        }

        askServer().closeTransactionSession(unit, settlement);
        if (unit.getMovesLeft() > 0) gui.setActiveUnit(unit); // No trade?
        unitWas.fireChanges();
        if (!unit.couldMove()) nextActiveUnit();
        return false;
    }

    /**
     * Displays an appropriate trade failure message.
     *
     * @param fail The failure state.
     * @param settlement The <code>Settlement</code> that failed to trade.
     * @param goods The <code>Goods</code> that failed to trade.
     * @return A <code>StringTemplate</code> describing the failure.
     */
    private StringTemplate tradeFailMessage(int fail, Settlement settlement,
                                            Goods goods) {
        switch (fail) {
        case NO_TRADE_GOODS:
            return StringTemplate.template("trade.noTradeGoods")
                .add("%goods%", goods.getNameKey());
        case NO_TRADE_HAGGLE:
            return StringTemplate.template("trade.noTradeHaggle");
        case NO_TRADE_HOSTILE:
            return StringTemplate.template("trade.noTradeHostile");
        case NO_TRADE: // Proposal was refused
        default:
            break;
        }
        return StringTemplate.template("trade.noTrade")
            .addName("%settlement%",
                settlement.getLocationNameFor(freeColClient.getMyPlayer()));
    }

    /**
     * User interaction for buying from the natives.
     *
     * @param unit The <code>Unit</code> that is trading.
     * @param settlement The <code>Settlement</code> that is trading.
     * @return A <code>StringTemplate</code> containing a message if
     *     there is problem, or null on success.
     */
    private StringTemplate attemptBuyFromSettlement(Unit unit,
                                                    Settlement settlement) {
        final Game game = freeColClient.getGame();
        Player player = freeColClient.getMyPlayer();
        Goods goods = null;

        // Get list of goods for sale
        List<Goods> forSale = askServer()
            .getGoodsForSaleInSettlement(game, unit, settlement);
        for (;;) {
            if (forSale.isEmpty()) { // Nothing to sell to the player
                return StringTemplate.template("trade.nothingToSell");
            }

            // Choose goods to buy
            List<ChoiceItem<Goods>> choices = new ArrayList<>();
            for (Goods g : forSale) {
                String label = Messages.message(g.getLabel(true));
                choices.add(new ChoiceItem<Goods>(label, g));
            }
            goods = gui.getChoice(true, unit.getTile(),
                                  Messages.message("buyProposition.text"),
                                  settlement, "buyProposition.nothing",
                                  choices);
            if (goods == null) break; // Trade aborted by the player

            int gold = -1; // Initially ask for a price
            for (;;) {
                gold = askServer().buyProposition(unit, settlement,
                    goods, gold);
                if (gold <= 0) {
                    return tradeFailMessage(gold, settlement, goods);
                }

                // Show dialog for buy proposal
                boolean canBuy = player.checkGold(gold);
                GUI.BuyAction act
                    = gui.getBuyChoice(unit, settlement, goods, gold, canBuy);
                if (act == null) break; // User cancelled
                switch (act) {
                case BUY: // Accept price, make purchase
                    if (askServer().buyFromSettlement(unit,
                            settlement, goods, gold)) {
                        updateAfterMove(); // Assume success
                        return null;
                    }
                    return abortTrade;
                case HAGGLE: // Try to negotiate a lower price
                    gold = gold * 9 / 10;
                    break;
                default:
                    logger.warning("showBuyDialog fail: " + act);
                    return null;
                }
            }
        }
        return abortTrade;
    }

    /**
     * User interaction for selling to the natives.
     *
     * @param unit The <code>Unit</code> that is trading.
     * @param settlement The <code>Settlement</code> that is trading.
     * @return A <code>StringTemplate</code> containing a message if
     *     there is problem, or null on success.
     */
    private StringTemplate attemptSellToSettlement(Unit unit,
                                                   Settlement settlement) {
        Goods goods = null;
        for (;;) {
            // Choose goods to sell
            List<ChoiceItem<Goods>> choices = new ArrayList<>();
            for (Goods g : unit.getGoodsList()) {
                String label = Messages.message(g.getLabel(true));
                choices.add(new ChoiceItem<Goods>(label, g));
            }
            goods = gui.getChoice(true, unit.getTile(),
                                  Messages.message("sellProposition.text"),
                                  settlement, "sellProposition.nothing",
                                  choices);
            if (goods == null) break; // Trade aborted by the player

            int gold = -1; // Initially ask for a price
            for (;;) {
                gold = askServer().sellProposition(unit, settlement,
                    goods, gold);
                if (gold <= 0) {
                    return tradeFailMessage(gold, settlement, goods);
                }

                // Show dialog for sale proposal
                GUI.SellAction act = gui.getSellChoice(unit, settlement, goods, gold);
                if (act == null) break; // Cancelled
                switch (act) {
                case SELL: // Accepted price, make the sale
                    if (askServer().sellToSettlement(unit,
                            settlement, goods, gold)) {
                        updateAfterMove(); // Assume success
                        return null;
                    }
                    return abortTrade;
                case HAGGLE: // Ask for more money
                    gold = (gold * 11) / 10;
                    break;
                case GIFT: // Decide to make a gift of the goods
                    askServer().deliverGiftToSettlement(unit,
                        settlement, goods);
                    return abortTrade;
                default:
                    logger.warning("showSellDialog fail: " + act);
                    return null;
                }
            }
        }
        return abortTrade;
    }

    /**
     * User interaction for delivering a gift to the natives.
     *
     * @param unit The <code>Unit</code> that is trading.
     * @param settlement The <code>Settlement</code> that is trading.
     * @return A <code>StringTemplate</code> containing a message if
     *     there is problem, or null on success.
     */
    private StringTemplate attemptGiftToSettlement(Unit unit,
                                                   Settlement settlement) {
        List<ChoiceItem<Goods>> choices = new ArrayList<>();
        for (Goods g : unit.getGoodsList()) {
            String label = Messages.message(g.getLabel(true));
            choices.add(new ChoiceItem<Goods>(label, g));
        }
        Goods goods = gui.getChoice(true, unit.getTile(),
                                    Messages.message("gift.text"), settlement,
                                    "cancel", choices);
        if (goods != null
            && askServer().deliverGiftToSettlement(unit, settlement, goods)) {
            return null;
        }
        return abortTrade;
    }

    /**
     * Demand a tribute.
     *
     * @param unit The <code>Unit</code> to perform the attack.
     * @param amount An amount of tribute to demand.
     * @param direction The direction in which to attack.
     * @return True if the unit can move further.
     */
    private boolean moveTribute(Unit unit, int amount, Direction direction) {
        final Game game = freeColClient.getGame();
        Player player = unit.getOwner();
        Tile tile = unit.getTile();
        Tile target = tile.getNeighbourOrNull(direction);
        Settlement settlement = target.getSettlement();
        Player other = settlement.getOwner();

        // Indians are easy and can use the basic tribute mechanism.
        if (settlement.getOwner().isIndian()) {
            UnitWas unitWas = new UnitWas(unit);
            if (askServer().demandTribute(unit, direction)) {
                // Assume tribute paid
                unitWas.fireChanges();
                updateAfterMove();
                nextActiveUnit();
            }
            return false;
        }
        
        // Europeans might be human players, so we convert to a diplomacy
        // dialog.
        DiplomaticTrade agreement
            = new DiplomaticTrade(game, TradeContext.TRIBUTE, player, other,
                                  null, 0);
        agreement.add(new StanceTradeItem(game, player, other, Stance.PEACE));
        agreement.add(new GoldTradeItem(game, other, player, amount));
        return moveDiplomacy(unit, direction, agreement);
    }

    /**
     * Move a missionary into a native settlement, following a move of
     * MoveType.ENTER_INDIAN_SETTLEMENT_WITH_MISSIONARY.
     *
     * @param unit The <code>Unit</code> that will enter the settlement.
     * @param direction The direction in which the Indian settlement lies.
     * @return True if the unit can move further.
     */
    private boolean moveUseMissionary(Unit unit, Direction direction) {
        IndianSettlement settlement
            = (IndianSettlement)getSettlementAt(unit.getTile(), direction);
        Player player = unit.getOwner();
        boolean canEstablish = !settlement.hasMissionary();
        boolean canDenounce = !canEstablish
            && !settlement.hasMissionary(player);
        clearGotoOrders(unit);

        // Offer the choices.
        GUI.MissionaryAction act = gui.getMissionaryChoice(unit, settlement,
            canEstablish, canDenounce);
        if (act == null) return true;
        switch (act) {
        case ESTABLISH_MISSION:
            if (askServer().missionary(unit, direction, false)) {
                if (settlement.hasMissionary(player)) {
                    gui.playSound("sound.event.missionEstablished");
                }
                player.invalidateCanSeeTiles();
                nextActiveUnit();
            }
            break;
        case DENOUNCE_HERESY:
            if (askServer().missionary(unit, direction, true)) {
                if (settlement.hasMissionary(player)) {
                    gui.playSound("sound.event.missionEstablished");
                }
                nextModelMessage();
                nextActiveUnit();
            }
            break;
        case INCITE_INDIANS:
            List<ChoiceItem<Player>> choices = new ArrayList<>();
            for (Player p : freeColClient.getGame().getLiveEuropeanPlayers(player)) {
                String label = Messages.message(p.getNationName());
                choices.add(new ChoiceItem<Player>(label, p));
            }
            Player enemy = gui.getChoice(true, unit.getTile(),
                Messages.message("missionarySettlement.inciteQuestion"), unit,
                "missionarySettlement.cancel", choices);
            if (enemy == null) return true;
            int gold = askServer().incite(unit, direction, enemy, -1);
            if (gold < 0) {
                // protocol fail
            } else if (!player.checkGold(gold)) {
                gui.showInformationMessage(settlement,
                    StringTemplate.template("missionarySettlement.inciteGoldFail")
                    .add("%player%", enemy.getName())
                    .addAmount("%amount%", gold));
            } else {
                if (gui.confirm(true, unit.getTile(),
                        StringTemplate.template("missionarySettlement.inciteConfirm")
                            .add("%player%", enemy.getName())
                            .addAmount("%amount%", gold),
                        unit, "yes", "no")) {
                    if (askServer().incite(unit, direction, enemy, gold) >= 0) {
                        updateAfterMove();
                    }
                }
                nextActiveUnit();
            }
            break;
        default:
            logger.warning("showUseMissionaryDialog fail");
            break;
        }
        return false;
    }


    // Trade route support.

    /**
     * Create a message about a trade route stop.
     *
     * @param key A message key.
     * @param stop The <code>TradeRouteStop</code> to mention.
     * @param player The <code>Player</code> who will see the message.
     * @return A message incorporating the stop.
     */
    private String stopMessage(String key, TradeRouteStop stop, Player player) {
        return Messages.message(StringTemplate.template(key)
            .addStringTemplate("%location%",
                stop.getLocation().getLocationNameFor(player)));
    }

    /**
     * Follows a trade route, doing load/unload actions, moving the unit,
     * and updating the stop and destination.
     *
     * @param unit The <code>Unit</code> on the route.
     * @param messages An optional list in which to retain any
     *     <code>ModelMessage</code>s generated.
     * @return True if the unit should keep moving, which can only
     *     happen if the trade route is found to be broken and the
     *     unit is thrown off it.
     */
    private boolean followTradeRoute(Unit unit, List<ModelMessage> messages) {
        final Player player = unit.getOwner();
        final TradeRoute tr = unit.getTradeRoute();
        final boolean detailed = freeColClient.getClientOptions()
            .getBoolean(ClientOptions.SHOW_GOODS_MOVEMENT);
        final boolean checkProduction = freeColClient.getClientOptions()
            .getBoolean(ClientOptions.STOCK_ACCOUNTS_FOR_PRODUCTION);
        final List<TradeRouteStop> stops = tr.getStops();
        TradeRouteStop stop;
        boolean result = false, more = true;

        // If required accumulate a summary of all the activity of
        // this unit on its trade route into this string buffer.
        LogBuilder lb = new LogBuilder((detailed && !tr.isSilent()) ? 256 : -1);
        lb.mark();

        outer: for (;;) {
            stop = unit.getStop();
            // Complain and return if the stop is no longer valid.
            if (!TradeRoute.isStopValid(unit, stop)) {
                lb.add(" ", stopMessage("tradeRoute.invalidStop", stop, player));
                clearOrders(unit);
                result = true;
                break;
            }

            // Is the unit at the stop already?
            boolean atStop = Map.isSameLocation(stop.getLocation(),
                                                unit.getLocation());
            if (atStop) {
                lb.mark();

                // Anything to unload?
                unloadUnitAtStop(unit, lb);

                // Anything to load?
                loadUnitAtStop(unit, lb);

                // Wrap load/unload messages.
                lb.grew(" ", stopMessage("tradeRoute.atStop", stop, player));

                // If the un/load consumed the moves, break now before
                // updating the stop.  This allows next move to arrive
                // here again having taken a second shot at
                // un/loading, but this time should not have consumed
                // the moves.
                if (unit.getMovesLeft() <= 0) break;

                // Try to find the next stop with work to do.
                int index = unit.validateCurrentStop();
                int next = index;
                for (;;) {
                    if (++next >= stops.size()) next = 0;
                    if (next == index) {
                        // No work was found anywhere on the trade
                        // route, so we should skip this unit.
                        lb.add(" ", Messages.message("tradeRoute.wait"));
                        askServer().changeState(unit, UnitState.SKIPPED);
                        break outer;
                    }
                    stop = stops.get(next);
                    if (!TradeRoute.isStopValid(unit, stop)) {
                        // Invalid stop found, throw the unit off the route.
                        lb.add(" ", stopMessage("tradeRoute.invalidStop",
                                                stop, player));
                        clearOrders(unit);
                        result = true;
                        break outer;
                    }
                    if (unit.hasWorkAtStop(stop, checkProduction)) break;
                }
                // A new stop was found, inform the server.
                if (!askServer().setCurrentStop(unit, next)) {
                    askServer().changeState(unit, UnitState.SKIPPED);
                    break outer;
                }

                // Add messages for any skipped stops now that we know
                // a valid one has been found.
                for (;;) {
                    if (++index >= stops.size()) index = 0;
                    if (index == next) break;
                    lb.add(" ", stopMessage("tradeRoute.skipStop",
                                            stops.get(index), player));
                }

                continue; // Stop was updated, loop.
            }

            // Not at stop, give up if no moves left or the path was
            // exhausted on a previous round.
            if (unit.getMovesLeft() <= 0
                || unit.getState() == UnitState.SKIPPED
                || !more) {
                lb.add(" ", stopMessage("tradeRoute.toStop", stop, player));
                break;
            }

            // Find a path to the stop.  Skip if none.
            Location destination = stop.getLocation();
            PathNode path = unit.findPath(destination);
            if (path == null) {
                lb.add(" ", stopMessage("tradeRoute.pathStop", stop, player));
                unit.setState(UnitState.SKIPPED);
                break;
            }

            // Try to follow the path.  Mark if the path is complete
            // but loop so as to check for unload before returning.
            more = movePath(unit, path);
            if (!more) unit.setState(UnitState.SKIPPED);
        }

        if (lb.grew()) {
            ModelMessage m = new ModelMessage(MessageType.GOODS_MOVEMENT,
                                              "tradeRoute.prefix", unit)
                .addName("%route%", tr.getName())
                .addStringTemplate("%unit%",
                    unit.getLabel(Unit.UnitLabelType.NATIONAL))
                .addName("%data%", lb.toString());
            if (messages != null) {
                messages.add(m);
            } else {
                player.addModelMessage(m);
            }
        }
        return result;
    }

    /**
     * Work out what goods to load onto a unit at a stop, and load them.
     *
     * @param unit The <code>Unit</code> to load.
     * @param lb A <code>LogBuilder</code> to update.
     * @return True if goods were loaded.
     */
    private boolean loadUnitAtStop(Unit unit, LogBuilder lb) {
        final Game game = freeColClient.getGame();
        final Colony colony = unit.getColony();
        final Location loc = (unit.isInEurope())
            ? unit.getOwner().getEurope()
            : colony;
        final TradeRouteStop stop = unit.getStop();
        boolean ret = false;

        // Make a list of goods to load at this stop.  Collapse multiple
        // loads of the same goods type.
        List<AbstractGoods> toLoad = new ArrayList<>();
        for (GoodsType type : stop.getCargo()) {
            AbstractGoods ag = AbstractGoods.findByType(type, toLoad);
            if (ag != null) {
                ag.setAmount(ag.getAmount() + GoodsContainer.CARGO_SIZE);
            } else {
                toLoad.add(new AbstractGoods(type, GoodsContainer.CARGO_SIZE));
            }
        }

        // Adjust for what is already being carried.
        Iterator<AbstractGoods> ig = toLoad.iterator();
        while (ig.hasNext()) {
            AbstractGoods ag = ig.next();
            int amount = unit.getGoodsCount(ag.getType());
            if (amount > 0) {
                amount = ag.getAmount() - amount;
                if (amount <= 0) {
                    // Already at capacity for this goods type.  Ignore it.
                    ig.remove();
                } else {
                    ag.setAmount(amount);
                }
            }
        }

        // Load as much as possible
        for (AbstractGoods ag : toLoad) {
            GoodsType type = ag.getType();
            int present, export, demand = ag.getAmount();
            if (unit.isInEurope()) {
                present = export = Integer.MAX_VALUE;
            } else {
                present = colony.getGoodsCount(ag.getType());
                export = colony.getExportAmount(ag.getType());
            }
            if (export > 0) {
                int amount = Math.min(demand, export);
                Goods cargo = new Goods(game, loc, type, amount);
                if (askLoadGoods(cargo, unit)) {
                    lb.add(" ", getLoadGoodsMessage(type, amount, present,
                                                    export, demand));
                    ret = true;
                }
            } else if (present > 0) {
                lb.add(" ", getLoadGoodsMessage(type, 0, present, 0, demand));
            }
        }
        return ret;
    }

    /**
     * Gets a message describing a goods loading.
     *
     * @param type The <code>GoodsType</code> the type of goods being loaded.
     * @param amount The amount of goods loaded.
     * @param present The amount of goods already at the location.
     * @param export The amount of goods available to export.
     * @param demand The amount of goods the unit should load according to
     *     the trade route orders.
     * @return A summary of the load.
     */
    private String getLoadGoodsMessage(GoodsType type,
                                       int amount, int present,
                                       int export, int demand) {
        String key;
        int more;

        if (demand < export) {
            key = "tradeRoute.loadStopImport";
            more = export - demand;
        } else if (present > export && demand > export) {
            key = (amount == 0) ? "tradeRoute.loadStopNoExport"
                : "tradeRoute.loadStopExport";
            more = present - export;
        } else {
            key = "tradeRoute.loadStop";
            more = -1; // not displayed
        }
        return Messages.message(StringTemplate.template(key)
            .addAmount("%amount%", amount)
            .add("%goods%", type.getNameKey())
            .addAmount("%more%", more));
    }

    /**
     * Work out what goods to unload from a unit at a stop, and unload them.
     *
     * @param unit The <code>Unit</code> to unload.
     * @param lb A <code>LogBuilder</code> to update.
     * @return True if something was unloaded.
     */
    private boolean unloadUnitAtStop(Unit unit, LogBuilder lb) {
        Colony colony = unit.getColony();
        TradeRouteStop stop = unit.getStop();
        final List<GoodsType> goodsTypesToLoad = stop.getCargo();
        boolean ret = false;

        // Unload everything that is on the carrier but not listed to
        // be loaded at this stop.
        Game game = freeColClient.getGame();
        for (Goods goods : unit.getCompactGoodsList()) {
            GoodsType type = goods.getType();
            if (goodsTypesToLoad.contains(type)) continue; // Keep this cargo.

            int present = goods.getAmount();
            int toUnload = present;
            int atStop = (colony == null) ? Integer.MAX_VALUE // Europe
                : colony.getImportAmount(type);
            int amount = toUnload;
            if (amount > atStop) {
                String locName = colony.getName();
                String overflow = Integer.toString(toUnload - atStop);
                int option = freeColClient.getClientOptions()
                    .getInteger(ClientOptions.UNLOAD_OVERFLOW_RESPONSE);
                switch (option) {
                case ClientOptions.UNLOAD_OVERFLOW_RESPONSE_ASK:
                    StringTemplate template
                        = StringTemplate.template("traderoute.warehouseCapacity")
                        .addStringTemplate("%unit%",
                            unit.getLabel(Unit.UnitLabelType.NATIONAL))
                        .addName("%colony%", locName)
                        .addName("%amount%", overflow)
                        .add("%goods%", goods.getNameKey());
                    if (!gui.confirm(true, colony.getTile(), template,
                                     unit, "yes", "no")) amount = atStop;
                    break;
                case ClientOptions.UNLOAD_OVERFLOW_RESPONSE_NEVER:
                    amount = atStop;
                    break;
                case ClientOptions.UNLOAD_OVERFLOW_RESPONSE_ALWAYS:
                    break;
                default:
                    logger.warning("Illegal UNLOAD_OVERFLOW_RESPONSE: "
                        + Integer.toString(option));
                    break;
                }
            }

            // Try to unload.
            Goods cargo = new Goods(game, unit, type, amount);
            ret = (cargo.getAmount() == 0) ? false
                : (unit.isInEurope())
                ? askSellGoods(cargo, unit, unit.getOwner().getEurope())
                : askUnloadGoods(cargo, unit, colony);
            lb.add(" ", getUnloadGoodsMessage(unit, type, amount,
                                              present, atStop, toUnload));
        }

        return ret;
    }

    /**
     * Gets a message describing a goods unloading.
     *
     * @param unit The <code>Unit</code> that is unloading.
     * @param type The <code>GoodsType</code> the type of goods being unloaded.
     * @param amount The amount of goods requested to be unloaded.
     * @param present The amount of goods originally on the unit.
     * @param atStop The amount of goods space available at the stop.
     * @param toUnload The amount of goods that should be unloaded according
     *     to the trade route orders.
     * @return A summary of the unload.
     */
    private String getUnloadGoodsMessage(Unit unit, GoodsType type,
                                         int amount, int present,
                                         int atStop, int toUnload) {
        String key = null;
        int onBoard = unit.getGoodsCount(type);
        int unloaded = present - onBoard;
        int more = 0;

        if (unloaded < amount) {
            key = "tradeRoute.unloadStopFail";
            more = unloaded;
        } else if (amount > atStop) {
            if (amount == toUnload) {
                key = "tradeRoute.unloadStopImport";
                more = toUnload - atStop;
            } else {
                key = (amount == 0) ? "tradeRoute.unloadStopNoExport"
                    : "tradeRoute.unloadStopExport";
                more = onBoard;
            }
        } else {
            key = "tradeRoute.unloadStop";
        }

        return Messages.message(StringTemplate.template(key)
            .addAmount("%amount%", amount)
            .addAmount("%more%", more)
            .add("%goods%", type.getNameKey()));
    }


    // All the routines from here on are user commands.  That is they
    // are called directly as a result of keyboard, menu, mouse or
    // panel/dialog actions.  They should all be annotated as such to
    // confirm where they can come from.
    //
    // They also all return a success/failure indication, except if
    // the game is stopped.

    /**
     * Abandon a colony with no units.
     *
     * Called from ColonyPanel.closeColonyPanel
     *
     * @param colony The <code>Colony</code> to be abandoned.
     * @return True if the colony was abandoned.
     */
    public boolean abandonColony(Colony colony) {
        if (!requireOurTurn()) return false;
        Player player = freeColClient.getMyPlayer();

        // Sanity check
        if (colony == null || !player.owns(colony)
            || colony.getUnitCount() > 0) {
            throw new RuntimeException("Abandon bogus colony");
        }

        // Proceed to abandon
        Tile tile = colony.getTile();
        boolean ret = askServer().abandonColony(colony)
            && !tile.hasSettlement();
        if (ret) {
            player.invalidateCanSeeTiles();
            gui.setActiveUnit(null);
            gui.setSelectedTile(tile, false);
        }
        return ret;
    }

    /**
     * Assigns a student to a teacher.
     *
     * Called from UnitLabel
     *
     * @param student The student <code>Unit</code>.
     * @param teacher The teacher <code>Unit</code>.
     * @return True if the student was assigned.
     */
    public boolean assignTeacher(Unit student, Unit teacher) {
        Player player = freeColClient.getMyPlayer();
        if (!requireOurTurn()
            || student == null
            || !player.owns(student)
            || student.getColony() == null
            || !student.isInColony()
            || teacher == null
            || !player.owns(teacher)
            || !student.canBeStudent(teacher)
            || teacher.getColony() == null
            || student.getColony() != teacher.getColony()
            || !teacher.getColony().canTrain(teacher)) {
            return false;
        }

        return askServer().assignTeacher(student, teacher);
    }

    /**
     * Assigns a trade route to a unit.
     *
     * Called from EuropePanel.DestinationPanel, TradeRoutePanel(),
     * TradeRoutePanel.newRoute
     *
     * @param unit The <code>Unit</code> to assign a trade route to.
     * @param tradeRoute The <code>TradeRoute</code> to assign.
     * @return True if the route was successfully assigned.
     */
    public boolean assignTradeRoute(Unit unit, TradeRoute tradeRoute) {
        if (tradeRoute == unit.getTradeRoute()) return true;

        if (tradeRoute != null && unit.getTradeRoute() != null) {
            if (!gui.confirmClearTradeRoute(unit)) return false;
        }

        return askServer().assignTradeRoute(unit, tradeRoute)
            && unit.getTradeRoute() == tradeRoute;
    }

    /**
     * Boards a specified unit onto a carrier.
     * The carrier must be at the same location as the boarding unit.
     *
     * Called from CargoPanel, TilePopup.
     *
     * @param unit The <code>Unit</code> which is to board the carrier.
     * @param carrier The carrier to board.
     * @return True if the unit boards the carrier.
     */
    public boolean boardShip(Unit unit, Unit carrier) {
        if (!requireOurTurn() || unit == null || carrier == null) return false;

        // Sanity checks.
        if (unit.isCarrier()) {
            throw new RuntimeException("Trying to load a carrier onto another carrier.");
        }
        if (!Map.isSameLocation(unit.getLocation(), carrier.getLocation())) {
            throw new RuntimeException("Unit and carrier are not co-located.");
        }

        // Proceed to board
        UnitWas unitWas = new UnitWas(unit);
        boolean ret = askServer().embark(unit, carrier, null)
            && unit.getLocation() == carrier;
        if (ret) {
            unitWas.fireChanges();
            gui.playSound("sound.event.loadCargo");
        }
        nextActiveUnit();
        return ret;
    }

    /**
     * Use the active unit to build a colony.
     *
     * Called from BuildColonyAction.
     *
     * @param unit The <code>Unit</code> to build the colony.
     * @return True if a colony was built.
     */
    public boolean buildColony(Unit unit) {
        if (!requireOurTurn()) return false;

        // Check unit, which must be on the map and able to build.
        if (unit == null) return false; 
        Tile tile = unit.getTile();
        if (tile == null) return false;
        if (!unit.canBuildColony()) {
            gui.showInformationMessage(unit,
                StringTemplate.template("buildColony.badUnit")
                    .addName("%unit%", unit.getName()));
            return false;
        }

        // Join existing colony if present
        Colony colony = tile.getColony();
        if (colony != null) {
            askServer().joinColony(unit, colony);
            gui.showColonyPanel(colony, unit);
            return false;
        }

        // Check for other impediments.
        final Player player = freeColClient.getMyPlayer();
        NoClaimReason reason = player.canClaimToFoundSettlementReason(tile);
        switch (reason) {
        case NONE:
        case NATIVES: // Tile can still be claimed
            break;
        default:
            gui.showInformationMessage("noClaimReason."
                + reason.toString().toLowerCase(Locale.US));
            return false;
        }

        // Show the warnings if applicable.
        if (freeColClient.getClientOptions()
            .getBoolean(ClientOptions.SHOW_COLONY_WARNINGS)) {
            String warnings = tile.getBuildColonyWarnings(unit);
            if (!gui.confirm(true, tile, StringTemplate.label(warnings), unit,
                             "buildColony.yes", "buildColony.no")) {
                return false;
            }
        }

        // Claim tile from other owners before founding a settlement.
        // Only native owners that we can steal, buy from, or use a
        // bonus center tile exception should be possible by this point.
        if (tile.getOwner() != null && !player.owns(tile)) {
            if (!askClaimTile(player, tile, unit,
                              player.getLandPrice(tile))) return false;
            // One more check that founding can now proceed.
            if (!player.canClaimToFoundSettlement(tile)) return false;
        }

        // Get and check the name.
        String name = player.getSettlementName(null);
        name = gui.getInput(true, tile, StringTemplate.key("nameColony.text"),
                            name, "nameColony.yes", "nameColony.no");
        if (name == null) return false;
        if (name.isEmpty()) {
            gui.showErrorMessage("enterSomeText");
            return false; // 0-length invalid.
        }
        if (player.getSettlementByName(name) != null) { // Must be unique
            gui.showInformationMessage(tile,
                StringTemplate.template("nameColony.notUnique")
                .addName("%name%", name));
            return false;
        }

        boolean ret = askServer().buildColony(name, unit)
            && tile.hasSettlement();
        if (ret) {
            player.invalidateCanSeeTiles();
            gui.playSound("sound.event.buildingComplete");
            gui.setActiveUnit(null);
            gui.setSelectedTile(tile, false);

            // Check units present for treasure cash-in as they are now
            // suddenly in-colony.
            for (Unit unitInTile : tile.getUnitList()) {
                checkCashInTreasureTrain(unitInTile);
            }
        }
        return ret;
    }

    /**
     * Buy goods in Europe.
     * The amount of goods is adjusted to the space in the carrier.
     *
     * Called from CargoPanel, TilePopup.
     *
     * @param type The type of goods to buy.
     * @param amount The amount of goods to buy.
     * @param carrier The <code>Unit</code> acting as carrier.
     * @return True if the purchase succeeds.
     */
    public boolean buyGoods(GoodsType type, int amount, Unit carrier) {
        if (!requireOurTurn() || type == null || amount <= 0
            || carrier == null) return false;

        // Sanity checks.  Should not happen!
        final Player player = freeColClient.getMyPlayer();
        if (!player.owns(carrier)) {
            throw new RuntimeException("Carrier owned by someone else.");
        } else if (!player.canTrade(type)) {
            throw new RuntimeException("Goods are boycotted.");
        }

        // Size check, if there are spare holds they can be filled, but...
        int toBuy = GoodsContainer.CARGO_SIZE;
        if (!carrier.hasSpaceLeft()) {
            // ...if there are no spare holds, we can only fill a hold
            // already partially filled with this type, otherwise fail.
            int partial = carrier.getGoodsContainer().getGoodsCount(type)
                % GoodsContainer.CARGO_SIZE;
            if (partial == 0) return false;
            toBuy -= partial;
        }
        if (amount < toBuy) toBuy = amount;

        // Check that the purchase is funded.
        Market market = player.getMarket();
        if (!player.checkGold(market.getBidPrice(type, toBuy))) {
            gui.showErrorMessage("notEnoughGold");
            return false;
        }

        // Try to purchase.
        int oldAmount = carrier.getGoodsContainer().getGoodsCount(type);
        int price = market.getCostToBuy(type);
        UnitWas unitWas = new UnitWas(carrier);
        boolean ret = askServer().buyGoods(carrier, type, toBuy)
            && carrier.getGoodsContainer().getGoodsCount(type) != oldAmount;
        if (ret) {
            unitWas.fireChanges();
            for (TransactionListener l : market.getTransactionListener()) {
                l.logPurchase(type, toBuy, price);
            }
            gui.updateMenuBar();
            gui.playSound("sound.event.loadCargo");
            nextModelMessage();
        }
        return ret;
    }

    /**
     * Changes the state of this <code>Unit</code>.
     *
     * Called from FortifyAction, SentryAction, TilePopup, UnitLabel
     *
     * @param unit The <code>Unit</code>
     * @param state The state of the unit.
     * @return True if the state was changed.
     */
    public boolean changeState(Unit unit, UnitState state) {
        if (!requireOurTurn()) return false;

        if (!unit.checkSetState(state)) {
            return false; // Don't log, this is not exceptional
        }

        // Check if this is a hostile fortification, and give the player
        // a chance to confirm.
        Player player = freeColClient.getMyPlayer();
        if (state == UnitState.FORTIFYING && unit.isOffensiveUnit()
            && !unit.hasAbility(Ability.PIRACY)) {
            Tile tile = unit.getTile();
            if (tile != null && tile.getOwningSettlement() != null) {
                Player enemy = tile.getOwningSettlement().getOwner();
                if (player != enemy
                    && player.getStance(enemy) != Stance.ALLIANCE) {
                    if (!gui.confirmHostileAction(unit, tile))
                        return false; // Aborted
                }
            }
        }

        boolean ret = askServer().changeState(unit, state)
            && unit.getState() == state;
        if (ret) {
            if (unit == gui.getActiveUnit() && !unit.couldMove()) {
                nextActiveUnit();
            } else {
                gui.refresh();
            }
        }
        return ret;
    }

    /**
     * Changes the work type of this <code>Unit</code>.
     *
     * Called from ImprovementAction.
     *
     * @param unit The <code>Unit</code>
     * @param improvementType a <code>TileImprovementType</code> value
     * @return True if the improvement was changed.
     */
    public boolean changeWorkImprovementType(Unit unit,
                                             TileImprovementType improvementType) {
        if (!requireOurTurn()) return false;

        if (!unit.checkSetState(UnitState.IMPROVING)
            || improvementType.isNatural()) {
            return false; // Don't log, this is not exceptional
        }

        final Player player = freeColClient.getMyPlayer();
        Tile tile = unit.getTile();
        if (!player.owns(tile)) {
            if (!askClaimTile(player, tile, unit,
                              player.getLandPrice(tile))) return false;
        }

        boolean ret = askServer().changeWorkImprovementType(unit,
                                                            improvementType);
        nextActiveUnit();
        return ret;
    }

    /**
     * Changes the work type of this <code>Unit</code>.
     *
     * Called from ColonyPanel.tryWork, UnitLabel
     *
     * @param unit The <code>Unit</code>
     * @param workType The new <code>GoodsType</code> to produce.
     * @return True if the work type was changed.
     */
    public boolean changeWorkType(Unit unit, GoodsType workType) {
        if (!requireOurTurn()) return false;

        UnitWas unitWas = new UnitWas(unit);
        boolean ret = askServer().changeWorkType(unit, workType)
            && unit.getWorkType() == workType;
        if (ret) {
            unitWas.fireChanges();
        }
        return ret;
    }

    /**
     * Check if a unit is a treasure train, and if it should be cashed in.
     * Transfers the gold carried by this unit to the {@link Player owner}.
     *
     * Called from TilePopup
     *
     * @param unit The <code>Unit</code> to be checked.
     * @return True if the unit was cashed in (and disposed).
     */
    public boolean checkCashInTreasureTrain(Unit unit) {
        if (!unit.canCarryTreasure() || !unit.canCashInTreasureTrain()
            || !requireOurTurn()) {
            return false; // Fail quickly if just not a candidate.
        }

        Tile tile = unit.getTile();
        final Europe europe = unit.getOwner().getEurope();
        if (europe == null || unit.isInEurope()) {
            ;// No need to check for transport.
        } else {
            int fee = unit.getTransportFee();
            StringTemplate template;
            if (fee == 0) {
                template = StringTemplate.template("cashInTreasureTrain.free");
            } else {
                int percent = getSpecification()
                    .getInteger(GameOptions.TREASURE_TRANSPORT_FEE);
                template = StringTemplate.template("cashInTreasureTrain.pay")
                    .addAmount("%fee%", percent);
            }
            if (!gui.confirm(true, unit.getTile(), template, unit,
                    "cashInTreasureTrain.yes", "cashInTreasureTrain.no"))
                return false;
        }

        // Update if cash in succeeds.
        UnitWas unitWas = new UnitWas(unit);
        boolean ret = askServer().cashInTreasureTrain(unit)
            && unit.isDisposed();
        if (ret) {
            unitWas.fireChanges();
            gui.updateMenuBar();
            gui.playSound("sound.event.cashInTreasureTrain");
            nextActiveUnit(tile);
        }
        return ret;
    }

    /**
     * Choose a founding father from an offered list.
     *
     * Called from GUI.showChooseFoundingFatherDialog
     *
     * @param ffs A list of <code>FoundingFather</code>s to choose from.
     * @param ff The chosen <code>FoundingFather</code> (may be null).
     * @return True if a father was chosen.
     */
    public boolean chooseFoundingFather(List<FoundingFather> ffs,
                                        FoundingFather ff) {
        Player player = freeColClient.getMyPlayer();
        player.setCurrentFather(ff);
        return askServer().chooseFoundingFather(ffs, ff);
    }

    /**
     * Claim a tile.
     *
     * Called from ColonyPanel.ASingleTilePanel, UnitLabel and work()
     *
     * @param tile The <code>Tile</code> to claim.
     * @param claimant The <code>Unit</code> or <code>Colony</code> claiming.
     * @return True if the claim succeeded.
     */
    public boolean claimTile(Tile tile, FreeColGameObject claimant) {
        if (!requireOurTurn()) return false;

        Player player = freeColClient.getMyPlayer();
        int price = ((claimant instanceof Settlement)
                ? player.canClaimForSettlement(tile)
                : player.canClaimForImprovement(tile))
            ? 0
            : player.getLandPrice(tile);
        return askClaimTile(player, tile, claimant, price);
    }

    /**
     * Clears the goto orders of the given unit by setting its destination
     * to null.
     *
     * Called from MapViewer.setSelectedTile
     *
     * @param unit The <code>Unit</code> to clear the destination for.
     * @return True if the unit has no destination.
     */
    public boolean clearGotoOrders(Unit unit) {
        if (unit == null || unit.getDestination() == null) return false;
        if (!gui.confirmClearTradeRoute(unit)) return false;

        boolean ret = askServer().setDestination(unit, null)
            && unit.getDestination() == null;
        if (ret) {
            if (unit == gui.getActiveUnit()) {
                gui.getMapViewer().updateCurrentPathForActiveUnit();
            }
        }
        return ret;
    }

    /**
     * Clears the orders of the given unit.
     * Make the unit active and set a null destination and trade route.
     *
     * Called from ClearOrdersAction, TilePopup, TradeRoutePanel, UnitLabel
     *
     * @param unit The <code>Unit</code> to clear the orders of
     * @return boolean <b>true</b> if the orders were cleared
     */
    public boolean clearOrders(Unit unit) {
        if (!requireOurTurn() || unit == null
            || !unit.checkSetState(UnitState.ACTIVE)) return false;

        if (unit.getState() == UnitState.IMPROVING
            && !gui.confirm(true, unit.getTile(),
                StringTemplate.template("model.unit.confirmCancelWork")
                    .addAmount("%turns%", unit.getWorkTurnsLeft()),
                unit, "ok", "cancel")) {
            return false;
        }

        if (unit.getTradeRoute() != null) {
            assignTradeRoute(unit, null);
        } else {
            clearGotoOrders(unit);
        }
        return askServer().changeState(unit, UnitState.ACTIVE);
    }

    /**
     * Clear the speciality of a Unit, making it a Free Colonist.
     *
     * Called from UnitLabel
     *
     * @param unit The <code>Unit</code> to clear the speciality of.
     * @return True if the speciality was cleared.
     */
    public boolean clearSpeciality(Unit unit) {
        if (!requireOurTurn()) return false;

        UnitType oldType = unit.getType();
        UnitType newType = oldType.getTargetType(ChangeType.CLEAR_SKILL,
                                                 unit.getOwner());
        if (newType == null) {
            gui.showInformationMessage(unit,
                StringTemplate.template("clearSpeciality.impossible")
                              .addStringTemplate("%unit%",
                                  unit.getLabel(Unit.UnitLabelType.NATIONAL)));
            return false;
        }

        Tile tile = (gui.isShowingSubPanel()) ? null : unit.getTile();
        if (!gui.confirm(true, tile,
                StringTemplate.template("clearSpeciality.areYouSure")
                              .addStringTemplate("%oldUnit%",
                                  unit.getLabel(Unit.UnitLabelType.NATIONAL))
                              .add("%unit%", newType.getNameKey()),
                unit, "ok", "cancel")) {
            return false;
        }

        // Try to clear.
        boolean ret = askServer().clearSpeciality(unit)
            && unit.getType() == newType;
        if (ret) {
            // Would expect to need to do:
            //    unit.firePropertyChange(Unit.UNIT_TYPE_CHANGE,
            //                            oldType, newType);
            // but this routine is only called out of UnitLabel, where the
            // unit icon is always updated anyway.
        }
        nextActiveUnit();
        return ret;
    }

    /**
     * Declares independence for the home country.
     *
     * Called from DeclareIndependenceAction
     *
     * @return True if independence was declared.
     */
    public boolean declareIndependence() {
        if (!requireOurTurn()) return false;
        final Player player = freeColClient.getMyPlayer();

        // Check for adequate support.
        StringTemplate declare = player.checkDeclareIndependence();
        if (declare != null) {
            gui.showInformationMessage(declare);
            return false;
        }
        if (player.getNewLandName() == null) {
            // Can only happen in debug mode.
            return false;
        }

        // Confirm intention, and collect nation+country names.
        List<String> names = gui.confirmDeclaration();
        if (names == null
            || names.get(0) == null || names.get(0).isEmpty()
            || names.get(1) == null || names.get(1).isEmpty()) {
            // Empty name => user cancelled.
            return false;
        }

        // Ask server.
        String nationName = names.get(0);
        String countryName = names.get(1);
        boolean ret = askServer().declareIndependence(nationName, countryName)
            && player.isRebel();
        if (ret) {
            freeColClient.updateActions();
            nextModelMessage();
            gui.showDeclarationPanel();
        }
        return ret;
    }

    /**
     * Handle a diplomatic offer.
     *
     * Called from IGIH.diplomacy
     *
     * @param our Our <code>FreeColGameObject</code> that is negotiating.
     * @param other The other <code>FreeColGameObject</code>.
     * @param agreement The <code>DiplomaticTrade</code> agreement.
     * @return A counter agreement, a rejected agreement, or null if
     *     the original agreement was already decided.
     */
    public DiplomaticTrade diplomacy(FreeColGameObject our,
                                     FreeColGameObject other,
                                     DiplomaticTrade agreement) {
        final Player player = freeColClient.getMyPlayer();
        final Player otherPlayer = agreement.getOtherPlayer(player);
        StringTemplate t, nation = otherPlayer.getNationName();

        switch (agreement.getStatus()) {
        case ACCEPT_TRADE:
            boolean visibilityChange = false;
            for (Colony c : agreement.getColoniesGivenBy(player)) {
                player.removeSettlement(c);//-vis(player)
                visibilityChange = true;
            }
            for (Unit u : agreement.getUnitsGivenBy(player)) {
                player.removeUnit(u);//-vis(player)
                visibilityChange = true;
            }
            if (visibilityChange) player.invalidateCanSeeTiles();//+vis(player)
            t = StringTemplate.template("negotiationDialog.offerAccepted")
                .addStringTemplate("%nation%", nation);
            gui.showInformationMessage(null, t);
            break;
        case REJECT_TRADE:
            t = StringTemplate.template("negotiationDialog.offerRejected")
                .addStringTemplate("%nation%", nation);
            gui.showInformationMessage(null, t);
            break;
        case PROPOSE_TRADE:
            String messageId = "negotiationDialog.receive."
                + agreement.getContext().getKey();
            t = StringTemplate.template(messageId)
                .addStringTemplate("%nation%", nation);
            DiplomaticTrade ourAgreement
                = gui.showDiplomaticTradeDialog(our, other, agreement, t);
            if (ourAgreement == null) {
                agreement.setStatus(TradeStatus.REJECT_TRADE);
            } else {
                agreement = ourAgreement;
            }
            return agreement;
        default:
            logger.warning("Bogus trade status: " + agreement.getStatus());
            break;
        }
        return null;
    }

    /**
     * Disbands the active unit.
     *
     * Called from DisbandUnitAction.
     *
     * @param unit The <code>Unit</code> to disband.
     * @return True if the unit was disbanded.
     */
    public boolean disbandUnit(Unit unit) {
        if (!requireOurTurn()) return false;
        if (unit == null) return false;
        if (unit.getColony() != null && !gui.confirmLeaveColony(unit))
            return false;

        Tile tile = (gui.isShowingSubPanel()) ? null
            : unit.getTile();
        if (!gui.confirm(true, tile, StringTemplate.key("disbandUnit.text"),
                         unit, "disbandUnit.yes", "disbandUnit.no"))
            return false;

        // Try to disband
        boolean ret = askServer().disbandUnit(unit);
        nextActiveUnit();
        return ret;
    }

    /**
     * Display the high scores.
     *
     * Called from IGIH.gameEnded, ReportHighScoresAction
     *
     * @param high A <code>Boolean</code> whose values indicates whether
     *     a new high score has been achieved, or no information if null.
     * @return True, the high scores were displayed.
     */
    public boolean displayHighScores(Boolean high) {
        List<HighScore> scores = askServer().getHighScores();
        gui.showHighScoresPanel((high == null) ? null
            : (high.booleanValue()) ? "highscores.yes" : "highscores.no",
            scores);
        return true;
    }

    /**
     * Displays pending <code>ModelMessage</code>s.
     *
     * Called from IGIH.displayModelMessagesRunnable
     *
     * @param allMessages Display all messages or just the undisplayed ones.
     * @return True if any messages were displayed.
     */
    public boolean displayModelMessages(boolean allMessages) {
        return displayModelMessages(allMessages, false);
    }

    /**
     * Emigrate a unit from Europe.
     *
     * Called from GUI.showEmigrationDialog
     *
     * @param player The <code>Player</code> that owns the unit.
     * @param slot The slot to emigrate from.
     * @return True if a unit emigrated.
     */
    public boolean emigrate(Player player, int slot) {
        if (!player.isColonial()) return false;

        return askEmigrate(player.getEurope(), slot) != null;
    }

    /**
     * End the turn command.
     *
     * Called from EndTurnAction, GUI.showEndTurnDialog
     *
     * @param showDialog If false, suppress showing the end turn dialog.
     * @return True if the turn was ended.
     */
    public boolean endTurn(boolean showDialog) {
        if (!requireOurTurn()) return false;

        return doEndTurn(showDialog && freeColClient.getClientOptions()
            .getBoolean(ClientOptions.SHOW_END_TURN_DIALOG));
    }

    /**
     * Change the role-equipment a unit has.
     *
     * Called from DefaultTransferHandler, QuickActionMenu
     *
     * @param unit The <code>Unit</code>.
     * @param role The <code>Role</code> to assume.
     * @param roleCount The role count.
     * @return True if the role was changed.
     */
    public boolean equipUnitForRole(Unit unit, Role role, int roleCount) {
        if (!requireOurTurn() || unit == null || role == null
            || roleCount < 0 || roleCount > role.getMaximumCount()
            || (role == unit.getRole() && roleCount == unit.getRoleCount())) {
            return false;
        }

        final Player player = freeColClient.getMyPlayer();
        final Colony colony = unit.getColony();
        ColonyWas colonyWas = null;
        EuropeWas europeWas = null;

        List<AbstractGoods> req = unit.getGoodsDifference(role, roleCount);
        if (unit.isInEurope()) {
            for (AbstractGoods ag : req) {
                GoodsType goodsType = ag.getType();
                if (!player.canTrade(goodsType) && !payArrears(goodsType)) {
                    return false; // payment failed
                }
            }
            int price = player.getEurope().priceGoods(req);
            if (price < 0 || !player.checkGold(price)) return false;
            europeWas = new EuropeWas(player.getEurope());
        } else if (colony != null) {
            for (AbstractGoods ag : req) {
                if (colony.getGoodsCount(ag.getType()) < ag.getAmount()) {
                    StringTemplate template = StringTemplate
                        .template("equipUnit.impossible")
                        .addName("%colony%", colony.getName())
                        .add("%equipment%", ag.getType().getNameKey())
                        .addStringTemplate("%unit%",
                            unit.getLabel(Unit.UnitLabelType.NATIONAL));
                    gui.showInformationMessage(unit, template);
                    return false;
                }
            }
            colonyWas = new ColonyWas(colony);
        } else {
            throw new IllegalStateException("Unit not in settlement/Europe");
        }

        UnitWas unitWas = new UnitWas(unit);
        boolean ret = askServer().equipUnitForRole(unit, role, roleCount)
            && unit.getRole() == role;
        if (ret) {
            if (colonyWas != null) colonyWas.fireChanges();
            if (europeWas != null) europeWas.fireChanges();
            unitWas.fireChanges();
            gui.updateMenuBar();
        }
        return ret;
    }

    /**
     * Execute goto orders command.
     *
     * Called from ExecuteGotoOrdersAction.
     *
     * @return True if all goto orders have been performed and no units
     *     reached their destination and are free to move again.
     */
    public boolean executeGotoOrders() {
        if (!requireOurTurn()) return false;

        return doExecuteGotoOrders();
    }

    /**
     * A player makes first contact with a native player.
     *
     * Called from GUI.showFirstContactDialog
     *
     * @param player The <code>Player</code> making contact.
     * @param other The native <code>Player</code> being contacted.
     * @param tile An optional <code>Tile</code> to offer the player if
     *     they have made a first landing.
     * @param result Whether the initial treaty was accepted.
     * @return True if first contact occurs.
     */
    public boolean firstContact(Player player, Player other, Tile tile,
                                boolean result) {
        return askServer().firstContact(player, other, tile, result);
    }

    /**
     * Retrieves client statistics.
     *
     * Called from StatisticsPanel
     *
     * @return A <code>Map</code> containing the client statistics.
     */
    public java.util.Map<String, String> getClientStatistics() {
        return freeColClient.getGame().getStatistics();
    }

    /**
     * Get the nation summary for a player.
     *
     * Called from DiplomaticTradePanel, ReportForeignAffairsPanel,
     * ReportIndianPanel
     *
     * @param player The <code>Player</code> to summarize.
     * @return A summary of that nation, or null on error.
     */
    public NationSummary getNationSummary(Player player) {
        return askServer().getNationSummary(player);
    }

    /**
     * Gets a new trade route for a player.
     *
     * Called from TradeRoutePanel.newRoute
     *
     * @param player The <code>Player</code> to get a new trade route for.
     * @return A new <code>TradeRoute</code>.
     */
    public TradeRoute getNewTradeRoute(Player player) {
        int n = player.getTradeRoutes().size();
        if (askServer().getNewTradeRoute()
            && player.getTradeRoutes().size() == n + 1) {
            return player.getTradeRoutes().get(n);
        }
        return null;
    }

    /**
     * Gathers information about the REF.
     *
     * Called from ReportNavalPanel, ReportMilitaryPanel
     *
     * @return a <code>List</code> value
     */
    public List<AbstractUnit> getREFUnits() {
        return (!requireOurTurn()) ? Collections.<AbstractUnit>emptyList()
            : askServer().getREFUnits();
    }

    /**
     * Retrieves the server statistics.
     *
     * Called from StatisticsPanel
     *
     * @return A <code>Map</code> containing the server statistics.
     */
    public java.util.Map<String, String> getServerStatistics() {
        return askServer().getStatistics();
    }

    /**
     * Go to a tile.
     *
     * Called from CanvasMouseListener, TilePopup
     *
     * @param unit The <code>Unit</code> to move.
     * @param tile The <code>Tile</code> to move to.
     * @return True if the destination change was successful.
     */
    public boolean goToTile(Unit unit, Tile tile) {
        if (!requireOurTurn() || unit == null) return false;

        if (!gui.confirmClearTradeRoute(unit)) return false;
        boolean ret = askServer().setDestination(unit, tile)
            && unit.getDestination() == tile;
        if (ret) {
            if (!moveToDestination(unit, null)) nextActiveUnit();
        }
        return ret;
    }

    /**
     * Ignore this ModelMessage from now on until it is not generated
     * in a turn.
     *
     * Called from ReportTurnPanel
     *
     * @param message a <code>ModelMessage</code> value
     * @param flag whether to ignore the ModelMessage or not
     * @return True, ignore message status changes can not fail.
     */
    public boolean ignoreMessage(ModelMessage message, boolean flag) {
        String key = message.getSourceId();
        if (message.getTemplateType() == StringTemplate.TemplateType.TEMPLATE) {
            for (String otherkey : message.getKeys()) {
                if ("%goods%".equals(otherkey)) {
                    key += otherkey;
                }
                break;
            }
        }
        if (flag) {
            Turn turn = freeColClient.getGame().getTurn();
            startIgnoringMessage(key, turn.getNumber());
        } else {
            stopIgnoringMessage(key);
        }
        return true;
    }

    /**
     * Handle a native demand at a colony.
     *
     * Called from IGIH.indianDemand
     *
     * @param unit The native <code>Unit</code> making the demand.
     * @param colony The <code>Colony</code> demanded of.
     * @param type The <code>GoodsType</code> demanded (null means gold).
     * @param amount The amount of goods/gold demanded.
     * @return Whether the demand was accepted or not.
     */
    public boolean indianDemand(Unit unit, Colony colony,
                                GoodsType type, int amount) {
        final Player player = freeColClient.getMyPlayer();
        final int opt = freeColClient.getClientOptions()
            .getInteger(ClientOptions.INDIAN_DEMAND_RESPONSE);

        boolean accepted;
        ModelMessage m = null;
        String nation = Messages.message(unit.getOwner().getNationName());
        if (type == null) {
            switch (opt) {
            case ClientOptions.INDIAN_DEMAND_RESPONSE_ASK:
                accepted = gui.confirm(true, colony.getTile(),
                    StringTemplate.template("indianDemand.gold.text")
                        .addName("%nation%", nation)
                        .addName("%colony%", colony.getName())
                        .addAmount("%amount%", amount),
                    unit, "indianDemand.gold.yes", "indianDemand.gold.no");
                break;
            case ClientOptions.INDIAN_DEMAND_RESPONSE_ACCEPT:
                m = new ModelMessage(ModelMessage.MessageType.DEMANDS,
                    "indianDemand.gold.text", colony, unit)
                    .addName("%nation%", nation)
                    .addName("%colony%", colony.getName())
                    .addAmount("%amount%", amount);
                accepted = true;
                break;
            case ClientOptions.INDIAN_DEMAND_RESPONSE_REJECT:
                m = new ModelMessage(ModelMessage.MessageType.DEMANDS,
                    "indianDemand.gold.text", colony, unit)
                    .addName("%nation%", nation)
                    .addName("%colony%", colony.getName())
                    .addAmount("%amount%", amount);
                accepted = false;
                break;
            default:
                throw new IllegalArgumentException("Impossible option value.");
            }
        } else {
            switch (opt) {
            case ClientOptions.INDIAN_DEMAND_RESPONSE_ASK:
                if (type.isFoodType()) {
                    accepted = gui.confirm(true, colony.getTile(),
                        StringTemplate.template("indianDemand.food.text")
                            .addName("%nation%", nation)
                            .addName("%colony%", colony.getName())
                            .addAmount("%amount%", amount),
                        unit, "indianDemand.food.yes", "indianDemand.food.no");
                } else {
                    accepted = gui.confirm(true, colony.getTile(),
                        StringTemplate.template("indianDemand.other.text")
                            .addName("%nation%", nation)
                            .addName("%colony%", colony.getName())
                            .addAmount("%amount%", amount)
                            .add("%goods%", type.getNameKey()),
                        unit, "indianDemand.other.yes", "indianDemand.other.no");
                }
                break;
            case ClientOptions.INDIAN_DEMAND_RESPONSE_ACCEPT:
                if (type.isFoodType()) {
                    m = new ModelMessage(ModelMessage.MessageType.DEMANDS,
                        "indianDemand.food.text", colony, unit)
                        .addName("%nation%", nation)
                        .addName("%colony%", colony.getName())
                        .addAmount("%amount%", amount);
                } else {
                    m = new ModelMessage(ModelMessage.MessageType.DEMANDS,
                        "indianDemand.other.text", colony, unit)
                        .addName("%nation%", nation)
                        .addName("%colony%", colony.getName())
                        .addAmount("%amount%", amount)
                        .add("%goods%", type.getNameKey());
                }
                accepted = true;
                break;
            case ClientOptions.INDIAN_DEMAND_RESPONSE_REJECT:
                if (type.isFoodType()) {
                    m = new ModelMessage(ModelMessage.MessageType.DEMANDS,
                        "indianDemand.food.text", colony, unit)
                        .addName("%nation%", nation)
                        .addName("%colony%", colony.getName())
                        .addAmount("%amount%", amount);
                } else {
                    m = new ModelMessage(ModelMessage.MessageType.DEMANDS,
                        "indianDemand.other.text", colony, unit)
                        .addName("%nation%", nation)
                        .addName("%colony%", colony.getName())
                        .addAmount("%amount%", amount)
                        .add("%goods%", type.getNameKey());
                }
                accepted = false;
                break;
            default:
                throw new IllegalArgumentException("Impossible option value.");
            }
        }
        if (m != null) {
            player.addModelMessage(m);
            nextModelMessage();
        }
        return accepted;
    }

    /**
     * Leave a ship.  The ship must be in harbour.
     *
     * Called from CargoPanel, ColonyPanel, EuropePanel.unloadAction,
     * UnitLabel
     *
     * @param unit The <code>Unit</code> which is to leave the ship.
     * @return True if the unit left the ship.
     */
    public boolean leaveShip(Unit unit) {
        if (!requireOurTurn()) return false;

        // Sanity check, and find our carrier before we get off.
        Unit carrier = unit.getCarrier();
        if (carrier == null) {
            logger.warning("Unit " + unit.getId() + " is not on a carrier.");
            return false;
        }

        // Proceed to disembark
        UnitWas unitWas = new UnitWas(unit);
        boolean ret = askServer().disembark(unit) && unit.getLocation() != carrier;
        if (ret) {
            checkCashInTreasureTrain(unit);
            unitWas.fireChanges();
        }
        nextActiveUnit();
        return ret;
    }

    /**
     * Loads a cargo onto a carrier.
     *
     * Called from CargoPanel, ColonyPanel, LoadAction, TilePopup.
     *
     * @param goods The <code>Goods</code> which are going aboard the carrier.
     * @param carrier The <code>Unit</code> acting as carrier.
     */
    public boolean loadCargo(Goods goods, Unit carrier) {
        if (!requireOurTurn()) return false;

        // Sanity checks.
        if (goods == null) {
            throw new IllegalArgumentException("Null goods.");
        } else if (goods.getAmount() <= 0) {
            throw new IllegalArgumentException("Empty goods.");
        } else if (carrier == null) {
            throw new IllegalArgumentException("Null carrier.");
        } else if (carrier.isInEurope()) {
            // empty
        } else if (carrier.getColony() == null) {
            throw new IllegalArgumentException("Carrier not at colony or Europe.");
        }

        // Try to load.
        return askLoadGoods(goods, carrier);
    }

    /**
     * Opens a dialog where the user should specify the filename and
     * loads the game.
     *
     * Called from OpenAction.
     *
     * Returns no status as this game is stopped.
     */
    public void loadGame() {
        File file = gui.showLoadDialog(FreeColDirectories.getSaveDirectory());
        if (file == null) return;
        if (!file.isFile()) {
            gui.showErrorMessage("fileNotFound");
            return;
        }
        if (freeColClient.isInGame() && !gui.confirmStopGame()) return;

        freeColClient.getConnectController().quitGame(true);
        turnReportMessages.clear();
        gui.setActiveUnit(null);
        gui.removeInGameComponents();
        freeColClient.getConnectController().startSavedGame(file, null);
    }

    /**
     * Loot some cargo.
     *
     * Called from GUI.showCaptureGoodsDialog
     *
     * @param unit The <code>Unit</code> that is looting.
     * @param goods A list of <code>Goods</code> to choose from.
     * @param defenderId The identifier of the defender unit (may have sunk).
     * @return True if looting occurs.
     */
    public boolean lootCargo(Unit unit, List<Goods> goods, String defenderId) {
        if (unit == null || goods.isEmpty()) return false;

        boolean ret = askServer().loot(unit, defenderId, goods);
        return ret;
    }

    /**
     * Accept or reject a monarch action.
     *
     * Called from GUI.showMonarchDialog
     *
     * @param action The <code>MonarchAction</code> performed.
     * @param accept If true, accept the action.
     * @return True if the monarch was answered.
     */
    public boolean monarchAction(MonarchAction action, boolean accept) {
        switch (action) {
        case RAISE_TAX_ACT: case RAISE_TAX_WAR:
        case MONARCH_MERCENARIES: case HESSIAN_MERCENARIES:
            return askServer().answerMonarch(action, accept);
        default:
            break;
        }
        return false;
    }

    /**
     * Moves the specified unit somewhere that requires crossing the
     * high seas.
     *
     * Called from EuropePanel.DestinationPanel, TilePopup
     *
     * @param unit The <code>Unit</code> to be moved.
     * @param destination The <code>Location</code> to be moved to.
     * @return True if the unit can possibly move further.
     */
    public boolean moveTo(Unit unit, Location destination) {
        if (!requireOurTurn()) return false;

        // Sanity check current state.
        if (unit == null || destination == null) {
            throw new IllegalArgumentException("moveTo null argument");
        } else if (destination instanceof Europe) {
            if (unit.isInEurope()) {
                gui.playSound("sound.event.illegalMove");
                return false;
            }
        } else if (destination instanceof Map) {
            if (unit.hasTile() && unit.getTile().getMap() == destination) {
                gui.playSound("sound.event.illegalMove");
                return false;
            }
        } else if (destination instanceof Settlement) {
            if (unit.hasTile()) {
                gui.playSound("sound.event.illegalMove");
                return false;
            }
        }

        // Autoload emigrants?
        if (freeColClient.getClientOptions()
            .getBoolean(ClientOptions.AUTOLOAD_EMIGRANTS)
            && unit.isInEurope()) {
            for (Unit u : unit.getOwner().getEurope().getUnitList()) {
                if (!u.isNaval()
                    && u.getState() == UnitState.SENTRY
                    && unit.canAdd(u)) {
                    boardShip(u, unit);
                }
            }
        }

        boolean ret = askServer().moveTo(unit, destination);
        if (ret) {
            ret = unit.getMovesLeft() > 0;
        } else {
            unit.setState(UnitState.SKIPPED);
        }
        return ret;
    }

    /**
     * Moves the active unit in a specified direction. This may result in an
     * attack, move... action.
     *
     * Called from MoveAction, CornerMapControls
     *
     * @param unit The <code>Unit</code> to move.
     * @param direction The <code>Direction</code> in which to move
     *     the active unit.
     * @return True if the unit may move further.
     */
    public boolean moveUnit(Unit unit, Direction direction) {
        if (!requireOurTurn() || unit == null) return false;

        unit.setState(UnitState.ACTIVE);
        clearGotoOrders(unit);
        boolean ret = moveDirection(unit, direction, true);
        if (ret) {
            updateAfterMove();
        } else {
            nextActiveUnit();
        }
        return ret;
    }

   /**
     * Move the tile cursor.
     *
     * Called from MoveAction in terrain mode.
     *
     * @param direction The <code>Direction</code> to move the tile cursor.
     * @return True if the tile cursor is moved.
     */
    public boolean moveTileCursor(Direction direction) {
        Tile tile = gui.getSelectedTile();
        if (tile == null) return false;

        Tile newTile = tile.getNeighbourOrNull(direction);
        if (newTile == null) return false;

        gui.setSelectedTile(newTile, false);
        return true;
    } 

    /**
     * A player names the New World.
     *
     * Called from GUI.showNameNewLandDialog
     *
     * @param unit The <code>Unit</code> that landed.
     * @param name The name to use.
     * @return True if the new land was named.
     */
    public boolean nameNewLand(Unit unit, String name) {
        // Respond to the server.
        if (!askServer().newLandName(unit, name)) return false;

        // The name is set, bring up the first landing panel.
        final Player player = unit.getOwner();
        StringTemplate t = StringTemplate.template("event.firstLanding")
            .addName("%name%", name);
        gui.showEventPanel(Messages.message(t), "EventImage.firstLanding",
                           null);

        // Add tutorial message.
        String key = FreeColActionUI.getHumanKeyStrokeText(freeColClient
            .getActionManager().getFreeColAction("buildColonyAction")
            .getAccelerator());
        player.addModelMessage(new ModelMessage(ModelMessage.MessageType.TUTORIAL,
                "tutorial.buildColony", player)
            .addName("%build_colony_key%", key)
            .add("%build_colony_menu_item%", "buildColonyAction.name")
            .add("%orders_menu_item%", "menuBar.orders"));
        nextModelMessage();
        return true;
    }

    /**
     * The player names a new region.
     *
     * Called from IGIH.newRegionName, GUI.showNameNewRegionDialog
     *
     * @param tile The <code>Tile</code> within the region.
     * @param unit The <code>Unit</code> that has discovered the region.
     * @param region The <code>Region</code> to name.
     * @param name The name to offer.
     * @return True if the new region was named.
     */
    public boolean nameNewRegion(final Tile tile, final Unit unit,
                                 final Region region, final String name) {
        return askServer().newRegionName(region, tile, unit, name);
    }

    /**
     * Switch to a new turn.
     *
     * Called from IGIH.newTurn
     *
     * @param turn The turn number.
     * @return True if the new turn occurs.
     */
    public boolean newTurn(int turn) {
        final Game game = freeColClient.getGame();
        final Player player = freeColClient.getMyPlayer();

        if (turn < 0) {
            logger.warning("Bad turn in newTurn: " + turn);
            return false;
        }
        game.setTurn(new Turn(turn));

        final boolean alert = freeColClient.getClientOptions()
            .getBoolean(ClientOptions.AUDIO_ALERTS);
        if (alert) gui.playSound("sound.event.alertSound");

        Turn currTurn = game.getTurn();
        if (currTurn.isFirstSeasonTurn()) {
            player.addModelMessage(new ModelMessage("twoTurnsPerYear", player)
                .addStringTemplate("%year%", currTurn.getLabel()));
        }
        return true;
    }

    /**
     * Makes a new unit active.
     *
     * Called from PGC.startGame, ColonyPanel.closeColonyPanel
     *
     * @return True unless it was not our turn.
     */
    public boolean nextActiveUnit() {
        if (!requireOurTurn()) return false;

        nextActiveUnit(null);
        return true;
    }

    /**
     * Displays the next <code>ModelMessage</code>.
     *
     * Called from CC.reconnect, CargoPanel,
     * ColonyPanel.closeColonyPanel, EuropePanel.exitAction,
     * EuropePanel.MarketPanel
     *
     * @return True if any messages were displayed.
     */
    public boolean nextModelMessage() {
        return displayModelMessages(false, false);
    }

    /**
     * Pays the tax arrears on this type of goods.
     *
     * Called from CargoPanel, EuropePanel.MarketPanel,
     * EuropePanel.unloadAction, QuickActionMenu
     *
     * @param type The type of goods for which to pay arrears.
     * @return True if the arrears were paid.
     */
    public boolean payArrears(GoodsType type) {
        if (!requireOurTurn()) return false;

        Player player = freeColClient.getMyPlayer();
        int arrears = player.getArrears(type);
        if (arrears <= 0) return false;
        if (!player.checkGold(arrears)) {
            gui.showInformationMessage(StringTemplate
                .template("model.europe.cantPayArrears")
                    .addAmount("%amount%", arrears));
            return false;
        }
        if (gui.confirm(true, null,
                StringTemplate.template("model.europe.payArrears")
                    .addAmount("%amount%", arrears),
                type, "ok", "cancel")
            && askServer().payArrears(type)
            && player.canTrade(type)) {
            gui.updateMenuBar();
            return true;
        }
        return false;
    }

    /**
     * Buys the remaining hammers and tools for the {@link Building} currently
     * being built in the given <code>Colony</code>.
     *
     * Called from BuildQueuePanel
     *
     * @param colony The <code>Colony</code> where the building should be
     *     bought.
     * @return True if the building was bought.
     */
    public boolean payForBuilding(Colony colony) {
        if (!requireOurTurn()) return false;

        if (!getSpecification().getBoolean(GameOptions.PAY_FOR_BUILDING)) {
            gui.showErrorMessage("payForBuilding.disabled");
            return false;
        }

        if (!colony.canPayToFinishBuilding()) {
            gui.showErrorMessage("notEnoughGold");
            return false;
        }
        int price = colony.getPriceForBuilding();
        if (!gui.confirm(true, null,
                         StringTemplate.template("payForBuilding.text")
                            .addAmount("%amount%", price),
                         colony, "payForBuilding.yes", "payForBuilding.no")) {
            return false;
        }

        ColonyWas colonyWas = new ColonyWas(colony);
        boolean ret = askServer().payForBuilding(colony)
            && colony.getPriceForBuilding() == 0;
        if (ret) {
            colonyWas.fireChanges();
            gui.updateMenuBar();
        }
        return ret;
    }

    /**
     * Puts the specified unit outside the colony.
     *
     * Called from ColonyPanel.OutsideColonyPanel, UnitLabel
     *
     * @param unit The <code>Unit</code>
     * @return True if the unit was successfully put outside the colony.
     */
    public boolean putOutsideColony(Unit unit) {
        if (!requireOurTurn()) return false;

        Colony colony = unit.getColony();
        if (colony == null) {
            throw new IllegalStateException("Unit is not in colony.");
        }
        if (!gui.confirmLeaveColony(unit)) return false;

        ColonyWas colonyWas = new ColonyWas(colony);
        UnitWas unitWas = new UnitWas(unit);
        boolean ret = askServer().putOutsideColony(unit)
            && unit.getLocation() == colony.getTile();
        if (ret) {
            colonyWas.fireChanges();
            unitWas.fireChanges();
        }
        return ret;
    }

    /**
     * Query whether the user wants to reconnect?
     *
     * Called from ReconnectAction, IGIH.reconnectRunnable
     *
     * Returns no status, this game is going away.
     */
    public void reconnect() {
        if (gui.confirm("reconnect.text", "reconnect.quit", "reconnect.yes")) {
            logger.finest("Reconnect quit.");
            freeColClient.quit();
        } else {
            logger.finest("Reconnect accepted.");
            freeColClient.getConnectController().reconnect();
        }
    }

    /**
     * Recruit a unit from a specified index in Europe.
     *
     * Called from RecruitPanel
     *
     * @param index The index in Europe to recruit from ([0..2]).
     * @return True if a unit was recruited.
     */
    public boolean recruitUnitInEurope(int index) {
        if (!requireOurTurn()) return false;

        final Player player = freeColClient.getMyPlayer();
        if (!player.isColonial()) return false;

        if (!player.checkGold(player.getRecruitPrice())) {
            gui.showErrorMessage("notEnoughGold");
            return false;
        }

        Unit newUnit = askEmigrate(player.getEurope(), index + 1);
        if (newUnit != null) {
            player.setNextActiveUnit(newUnit);
            gui.setActiveUnit(newUnit);
        }
        return newUnit != null;
    }

    /**
     * Renames a <code>Nameable</code>.
     *
     * Apparently this can be done while it is not your turn.
     *
     * Called from RenameAction, TilePopup.
     *
     * @param object The object to rename.
     * @return True if the object was renamed.
     */
    public boolean rename(Nameable object) {
        Player player = freeColClient.getMyPlayer();
        if (!(object instanceof Ownable)
            || !player.owns((Ownable) object)) {
            return false;
        }

        String name = null;
        if (object instanceof Colony) {
            Colony colony = (Colony) object;
            name = gui.getInput(true, colony.getTile(),
                                StringTemplate.key("renameColony.text"),
                                colony.getName(),
                                "renameColony.yes", "renameColony.no");
            if (name == null) { // User cancelled
                return false;
            } else if (name.isEmpty()) { // Zero length invalid
                gui.showErrorMessage("enterSomeText");
                return false;
            } else if (colony.getName().equals(name)) { // No change
                return false;
            } else if (player.getSettlementByName(name) != null) {
                // Colony name must be unique.
                gui.showInformationMessage((Colony) object,
                    StringTemplate.template("nameColony.notUnique")
                    .addName("%name%", name));
                return false;
            }
        } else if (object instanceof Unit) {
            Unit unit = (Unit) object;
            name = gui.getInput(true, unit.getTile(),
                                StringTemplate.key("renameUnit.text"),
                                unit.getName(),
                                "renameUnit.yes", "renameUnit.no");
            if (name == null) return false; // User cancelled
        } else {
            logger.warning("Tried to rename an unsupported Nameable: "
                + object);
            return false;
        }

        return askServer().rename((FreeColGameObject)object, name);
    }

    /**
     * Opens a dialog where the user should specify the filename and
     * saves the game.
     *
     * Called from SaveAction and SaveAndQuitAction.
     *
     * @return True if the game was saved.
     */
    public boolean saveGame() {
        if (!freeColClient.canSaveCurrentGame()) return false;

        Player player = freeColClient.getMyPlayer();
        Game game = freeColClient.getGame();
        if (game == null) return false; // Keyboard handling can race init

        String gid = Integer.toHexString(game.getUUID().hashCode());
        String fileName = /* player.getName() + "_" */ gid + "_"
            + Messages.message(player.getNationName()) + "_"
            + getSaveGameString(game.getTurn());
        fileName = fileName.replaceAll(" ", "_");

        File file = gui.showSaveDialog(FreeColDirectories.getSaveDirectory(),
                                       fileName);
        if (file == null) return false;
        
        final boolean confirm = freeColClient.getClientOptions()
            .getBoolean(ClientOptions.CONFIRM_SAVE_OVERWRITE);
        if (!confirm
            || !file.exists()
            || gui.confirm("saveConfirmationDialog.areYouSure.text",
                           "ok", "cancel")) {
            FreeColDirectories.setSaveDirectory(file.getParentFile());
            return saveGame(file);
        }
        return false;
    }

    /**
     * Selects a destination for this unit. Europe and the player's
     * colonies are valid destinations.
     *
     * Called from GotoAction.
     *
     * @param unit The unit for which to select a destination.
     * @return True if the destination change succeeds.
     */
    public boolean selectDestination(Unit unit) {
        if (!requireOurTurn()) return false;

        if (!gui.confirmClearTradeRoute(unit)) return false;
        Location destination = gui.showSelectDestinationDialog(unit);
        if (destination == null) return false;

        boolean ret = askServer().setDestination(unit, destination)
            && unit.getDestination() == destination;
        if (ret) {
            if (destination instanceof Europe) {
                if (unit.hasTile()
                    && unit.getTile().isDirectlyHighSeasConnected()) {
                    moveTo(unit, destination);
                } else {
                    moveToDestination(unit, null);
                }
            } else {
                if (unit.isInEurope()) {
                    moveTo(unit, destination);
                } else {
                    moveToDestination(unit, null);
                }
            }
            nextActiveUnit(); // Unit may have become unmovable.
        }
        return ret;
    }

    /**
     * Sells goods in Europe.
     *
     * Called from EuropePanel.MarketPanel, EuropePanel.unloadAction
     *
     * @param goods The goods to be sold.
     * @return True if the sale succeeds.
     */
    public boolean sellGoods(Goods goods) {
        if (!requireOurTurn() || goods == null) return false;

        // Sanity checks.
        final Player player = freeColClient.getMyPlayer();
        Unit carrier = null;
        if (goods.getLocation() instanceof Unit) {
            carrier = (Unit)goods.getLocation();
        }
        if (carrier == null) {
            throw new RuntimeException("Goods not on carrier.");
        } else if (!carrier.isInEurope()) {
            throw new RuntimeException("Goods not on carrier in Europe.");
        } else if (!player.canTrade(goods.getType())) {
            throw new RuntimeException("Goods are boycotted.");
        }

        return askSellGoods(goods, carrier, player.getEurope());
    }

    /**
     * Sends a public chat message.
     *
     * Called from ChatPanel
     *
     * @param chat The text of the message.
     * @return True if the message was sent.
     */
    public boolean sendChat(String chat) {
        return askServer().chat(freeColClient.getMyPlayer(), chat);
    }

    /**
     * Changes the current construction project of a <code>Colony</code>.
     *
     * Called from BuildQueuePanel
     *
     * @param colony The <code>Colony</code>
     * @param buildQueue List of <code>BuildableType</code>
     * @return True if the build queue was changed.
     */
    public boolean setBuildQueue(Colony colony, List<BuildableType> buildQueue) {
        if (!requireOurTurn()) return false;

        ColonyWas colonyWas = new ColonyWas(colony);
        boolean ret = askServer().setBuildQueue(colony, buildQueue);
        if (ret) {
            colonyWas.fireChanges();
        }
        return ret;
    }

    /**
     * Set a player to be the new current player.
     *
     * Called from IGIH.newTurn, IGIH.setCurrentPlayer, CC.login
     *
     * @param player The <code>Player</code> to be the new current player.
     * @return True if the current player changes.
     */
    public boolean setCurrentPlayer(Player player) {
        if (FreeColDebugger.isInDebugMode(FreeColDebugger.DebugMode.MENUS)
            && freeColClient.currentPlayerIsMyPlayer()) {
            gui.closeMenus();
        }
        FreeColDebugger.finishDebugRun(freeColClient, false);

        final Game game = freeColClient.getGame();
        game.setCurrentPlayer(player);
        if (freeColClient.getMyPlayer().equals(player)) {
            if (FreeColDebugger.isInDebugMode(FreeColDebugger.DebugMode.DESYNC)
                && DebugUtils.checkDesyncAction(freeColClient)) {
                freeColClient.getConnectController().reconnect();
                return false;
            }

            // Get turn report out quickly before more message display occurs.
            player.removeDisplayedModelMessages();
            displayModelMessages(true, true);

            player.invalidateCanSeeTiles();

            // Save the game (if it isn't newly loaded)
            if (freeColClient.getFreeColServer() != null
                && game.getTurn().getNumber() > 0) autoSaveGame();

            // Check for emigration.
            if (player.hasAbility(Ability.SELECT_RECRUIT)) {
                if (player.checkEmigrate()) {
                    if (player.getEurope().recruitablesDiffer()) {
                        gui.showEmigrationDialog(player, 1, false);
                    } else {
                        emigrate(player, 1);
                    }
                }
            } else {
                while (player.checkEmigrate()) emigrate(player, 0);
            }
            
            try {
                List<Settlement> settlements = player.getSettlements();
                Tile defTile = ((settlements.isEmpty())
                    ? player.getEntryLocation().getTile()
                    : settlements.get(0).getTile()).getSafeTile(null, null);
                player.resetIterators();
                nextActiveUnit(defTile);
            } catch (Exception e) {
                // We end up here if there is a crash in things like the
                // turn report.  These were hard to track down because we
                // used to fail silently.  We now complain louder.
                logger.log(Level.WARNING, "Client new turn failure for "
                    + player, e);
            }

            // GUI management.
            if (!freeColClient.isSinglePlayer()) {
                gui.playSound("sound.anthem." + player.getNationId());
            }
        }
        freeColClient.updateActions();
        return true;
    }

    /**
     * Set a player to be dead.
     *
     * Called from IGIH.setDead
     *
     * @param dead The dead <code>Player</code>.
     * @return True if the player is marked as dead.
     */
    public boolean setDead(Player dead) {
        final Player player = freeColClient.getMyPlayer();
        
        if (player == dead) {
            FreeColDebugger.finishDebugRun(freeColClient, true);
            if (freeColClient.isSinglePlayer()) {
                if (player.getPlayerType() == Player.PlayerType.RETIRED) {
                    ; // Do nothing, retire routine will quit

                } else if (player.getPlayerType() != Player.PlayerType.UNDEAD
                    && gui.confirm("defeatedSinglePlayer.text",
                                   "defeatedSinglePlayer.yes",
                                   "defeatedSinglePlayer.no")) {
                    freeColClient.askServer().enterRevengeMode();
                } else {
                    freeColClient.quit();
                }
            } else {
                if (!gui.confirm("defeated.text", "defeated.yes",
                                 "defeated.no")) freeColClient.quit();
            }
        } else {
            player.setStance(dead, null);
        }
        return true;
    }

    /**
     * Informs this controller that a game has been newly loaded.
     *
     * Called from ConnectController.startSavedGame
     *
     * No status returned to connect controller.
     */
    public void setGameConnected () {
        final Player player = freeColClient.getMyPlayer();
        if (player != null) {
            player.refilterModelMessages(freeColClient.getClientOptions());
        }
        updateAfterMove();
    }

    /**
     * Sets the export settings of the custom house.
     *
     * Called from WarehouseDialog
     *
     * @param colony The colony with the custom house.
     * @param goodsType The goods for which to set the settings.
     * @return True if the levels were set.
     */
    public boolean setGoodsLevels(Colony colony, GoodsType goodsType) {
        return askServer().setGoodsLevels(colony,
                                          colony.getExportData(goodsType));
    }

    /**
     * Sets the debug mode to include the extra menu commands.
     *
     * Called from DebugAction
     *
     * @return True, always succeeds.
     */
    public boolean setInDebugMode() {
        FreeColDebugger.enableDebugMode(FreeColDebugger.DebugMode.MENUS);
        updateAfterMove();
        return true;
    }

    /**
     * Notify the player that the stance between two players has changed.
     *
     * Called from IGIH.setStance
     *
     * @param stance The changed <code>Stance</code>.
     * @param first The first <code>Player</code>.
     * @param second The second <code>Player</code>.
     * @return True if the stance change succeeds.
     */
    public boolean setStance(Stance stance, Player first, Player second) {
        final Player player = freeColClient.getMyPlayer();

        Stance old = first.getStance(second);
        try {
            first.setStance(second, stance);
        } catch (IllegalStateException e) {
            logger.log(Level.WARNING, "Illegal stance transition", e);
            return false;
        }
        if (player == first && old == Stance.UNCONTACTED) {
            gui.playSound("sound.event.meet." + second.getNationId());
        }
        return true;
    }

    /**
     * Sets the trade routes for this player
     *
     * Called from TradeRoutePanel.deleteTradeRoute
     *
     * @param routes The trade routes to set.
     * @return True if the trade routes were set.
     */
    public boolean setTradeRoutes(List<TradeRoute> routes) {
        return askServer().setTradeRoutes(routes);
    }

    /**
     * Skip the active unit, note no server interaction.
     *
     * Called from SkipUnitAction
     *
     * @param unit The <code>Unit</code> to skip.
     * @return True if the active unit is not null.
     */
    public boolean skipUnit(Unit unit) {
        if (!requireOurTurn()) return false;
        if (unit == null) return false;
        if (unit.getState() == UnitState.SKIPPED) return true;

        unit.setState(UnitState.SKIPPED);
        nextActiveUnit();
        return true;
    }

    /**
     * Trains a unit of a specified type in Europe.
     *
     * Called from NewUnitPanel
     *
     * @param unitType The type of unit to be trained.
     * @return True if a new unit was trained.
     */
    public boolean trainUnitInEurope(UnitType unitType) {
        if (!requireOurTurn()) return false;

        Player player = freeColClient.getMyPlayer();
        Europe europe = player.getEurope();
        if (!player.checkGold(europe.getUnitPrice(unitType))) {
            gui.showErrorMessage("notEnoughGold");
            return false;
        }

        EuropeWas europeWas = new EuropeWas(europe);
        boolean ret = false;
        if (askServer().trainUnitInEurope(unitType)) {
            gui.updateMenuBar();
            europeWas.fireChanges();
            Unit newUnit = europeWas.getNewUnit();
            if (newUnit != null) {
                player.setNextActiveUnit(newUnit);
                gui.setActiveUnit(newUnit);
                ret = true;
            }
        }
        return ret;
    }

    /**
     * Unload, including dumping cargo.
     *
     * Called from UnloadAction, UnitLabel
     *
     * @param unit The <code>Unit<code> that is dumping.
     * @return True if the unit unloaded.
     */
    public boolean unload(Unit unit) {
        if (!requireOurTurn()) return false;

        // Sanity tests.
        if (unit == null) {
            throw new IllegalArgumentException("Null unit.");
        } else if (!unit.isCarrier()) {
            throw new IllegalArgumentException("Unit is not a carrier.");
        }

        Player player = freeColClient.getMyPlayer();
        boolean inEurope = unit.isInEurope();
        Colony colony = unit.getColony();
        if (colony != null) {
            // In colony, unload units and goods.
            for (Unit u : unit.getUnitList()) {
                leaveShip(u);
            }
            for (Goods goods : unit.getGoodsList()) {
                askUnloadGoods(goods, unit, colony);
            }
        } else {
            if (inEurope) { // In Europe, unload non-boycotted goods
                for (Goods goods : unit.getCompactGoodsList()) {
                    if (player.canTrade(goods.getType())) {
                        unloadCargo(goods, false);
                    }
                }
            }
            // Goods left here must be dumped.
            if (unit.hasGoodsCargo()) {
                gui.showDumpCargoDialog(unit);
            }
        }
        return true;
    }

    /**
     * Unload cargo.  If the unit carrying the cargo is not in a
     * harbour, or if the given boolean is true, the goods will be
     * dumped.
     *
     * Called from CargoPanel, ColonyPanel, EuropePanel.MarketPanel,
     * GUI.showDumpCargoDialog, QuickActionMenu
     *
     * @param goods The <code>Goods<code> to unload.
     * @param dump If true, dump the goods.
     * @return True if the unload succeeds.
     */
    public boolean unloadCargo(Goods goods, boolean dump) {
        if (!requireOurTurn()) return false;

        // Sanity tests.
        if (goods == null) {
            throw new RuntimeException("Null goods.");
        } else if (goods.getAmount() <= 0) {
            throw new RuntimeException("Empty goods.");
        }
        Unit carrier = null;
        if (!(goods.getLocation() instanceof Unit)) {
            throw new RuntimeException("Unload from non-unit.");
        }
        carrier = (Unit)goods.getLocation();
        if (dump) {
            gui.showDumpCargoDialog(carrier);
            return false;
        }
        if (carrier.isInEurope()) {
            return askSellGoods(goods, carrier, carrier.getOwner().getEurope());
        }
        if (!carrier.hasTile()) {
            throw new RuntimeException("Carrier with null location.");
        }
        Colony colony = carrier.getColony();
        if (colony == null) {
            throw new RuntimeException("Carrier not in colony.");
        }
        return askUnloadGoods(goods, carrier, colony);
    }

    /**
     * Updates a trade route.
     *
     * Called from TradeRoutePanel(), TradeRoutePanel.newRoute
     *
     * @param route The trade route to update.
     * @return True if the trade route was updated.
     */
    public boolean updateTradeRoute(TradeRoute route) {
        return askServer().updateTradeRoute(route);
    }

    /**
     * The player has won!
     *
     * Called from GUI.showVictoryDialog
     *
     * @return True.
     */
    public boolean victory(Boolean quit) {
        if (quit) {
            freeColClient.quit();
        } else {
            askServer().continuePlaying();
        }
        return true;
    }
        
    /**
     * Tell a unit to wait.
     *
     * Called from WaitAction.
     *
     * @return True, this can not fail.
     */
    public boolean waitUnit() {
        gui.setActiveUnit(null);
        nextActiveUnit();
        return true;
    }

    /**
     * Moves a <code>Unit</code> to a <code>WorkLocation</code>.
     *
     * Called from ColonyPanel.tryWork, UnitLabel
     *
     * @param unit The <code>Unit</code>.
     * @param workLocation The new <code>WorkLocation</code>.
     * @return True if the unit is now working at the new work location.
     */
    public boolean work(Unit unit, WorkLocation workLocation) {
        if (!requireOurTurn()) return false;

        StringTemplate template;
        if (unit.getStudent() != null
            && (template = unit.getAbandonEducationMessage(false)) != null
            && !gui.confirm(true, unit.getTile(), template,
                            unit, "abandonTeaching.yes", "abandonTeaching.no"))
            return false;

        Colony colony = workLocation.getColony();
        if (workLocation instanceof ColonyTile) {
            Tile tile = ((ColonyTile)workLocation).getWorkTile();
            if (tile.hasLostCityRumour()) {
                gui.showInformationMessage("tileHasRumour");
                return false;
            }
            if (!unit.getOwner().owns(tile)) {
                if (!claimTile(tile, colony)) return false;
            }
        }

        // Try to change the work location.
        ColonyWas colonyWas = new ColonyWas(colony);
        UnitWas unitWas = new UnitWas(unit);
        boolean ret = askServer().work(unit, workLocation)
            && unit.getLocation() == workLocation;
        if (ret) {
            colonyWas.fireChanges();
            unitWas.fireChanges();
        }
        return ret;
    }
}
