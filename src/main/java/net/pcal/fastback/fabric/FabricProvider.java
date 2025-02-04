/*
 * FastBack - Fast, incremental Minecraft backups powered by Git.
 * Copyright (C) 2022 pcal.net
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; If not, see <http://www.gnu.org/licenses/>.
 */

package net.pcal.fastback.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.world.level.storage.LevelStorage;
import net.pcal.fastback.ModContext;
import net.pcal.fastback.fabric.mixins.ServerAccessors;
import net.pcal.fastback.fabric.mixins.SessionAccessors;
import net.pcal.fastback.logging.Log4jLogger;
import net.pcal.fastback.logging.Logger;
import net.pcal.fastback.logging.Message;
import org.apache.logging.log4j.LogManager;

import java.nio.file.Path;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * @author pcal
 * @since 0.1.0
 */
public abstract class FabricProvider implements ModContext.FrameworkServiceProvider {

    private static FabricProvider INSTANCE;
    private MinecraftServer minecraftServer;
    private Runnable autoSaveListener;

    public static FabricProvider getInstance() {
        if (INSTANCE == null) throw new IllegalStateException("not initialized");
        return INSTANCE;
    }

    private static final String MOD_ID = "fastback";
    private boolean isWorldSaveEnabled = true;
    private final Logger logger = new Log4jLogger(LogManager.getLogger(MOD_ID));

    protected FabricProvider() {
        if (INSTANCE != null) throw new IllegalStateException();
        INSTANCE = this;
    }

    void setMinecraftServer(MinecraftServer serverOrNull) {
        if ((serverOrNull == null) == (this.minecraftServer == null)) throw new IllegalStateException();
        this.minecraftServer = serverOrNull;
    }

    @Override
    public Logger getLogger() {
        return this.logger;
    }

    @Override
    public String getMinecraftVersion() {
        return SharedConstants.getGameVersion().getName();
    }

    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public String getModId() {
        return MOD_ID;
    }

    @Override
    public String getModVersion() {
        Optional<ModContainer> optionalModContainer = FabricLoader.getInstance().getModContainer(MOD_ID);
        if (optionalModContainer.isEmpty()) {
            throw new IllegalStateException("Could not find loader for " + MOD_ID);
        }
        final ModMetadata m = optionalModContainer.get().getMetadata();
        return String.valueOf(m.getVersion());
    }

    @Override
    public boolean isWorldSaveEnabled() {
        return this.isWorldSaveEnabled;
    }

    @Override
    public void setWorldSaveEnabled(boolean enabled) {
        this.isWorldSaveEnabled = enabled;
    }

    @Override
    public void saveWorld() {
        if (this.minecraftServer == null) throw new IllegalStateException();
        this.minecraftServer.saveAll(false, true, true); // suppressLogs, flush, force
    }

    @Override
    public boolean isServerStopping() {
        if (this.minecraftServer == null) throw new IllegalStateException();
        return this.minecraftServer.isStopped() || this.minecraftServer.isStopping();
    }

    @Override
    public void sendFeedback(Message message, ServerCommandSource scs) {
        scs.sendFeedback(messageToText(message), false);
    }

    @Override
    public void sendError(Message message, ServerCommandSource scs) {
        scs.sendError(messageToText(message));
    }

    @Override
    public void setAutoSaveListener(Runnable runnable) {
        if (this.autoSaveListener != null) throw new IllegalStateException();
        this.autoSaveListener = requireNonNull(runnable);
    }

    @Override
    public Path getWorldDirectory() {
        if (this.minecraftServer == null) throw new IllegalStateException();
        final LevelStorage.Session session = ((ServerAccessors) this.minecraftServer).getSession();
        return ((SessionAccessors) session).getDirectory();
    }

    @Override
    public String getWorldName() {
        if (this.minecraftServer == null) throw new IllegalStateException();
        final LevelStorage.Session session = ((ServerAccessors) this.minecraftServer).getSession();
        return session.getLevelSummary().getLevelInfo().getLevelName();
    }

    public void autoSaveCompleted() {
        if (this.autoSaveListener != null) {
            this.autoSaveListener.run();
        } else {
            this.getLogger().warn("Autosave just happened but, unexpectedly, no one is listening.");
        }
    }

    static Text messageToText(final Message m) {
        if (m.localized() != null) {
            return new TranslatableText(m.localized().key(), m.localized().params());
        } else {
            return new LiteralText(m.raw());
        }
    }
}
