package com.bgsoftware.superiorskyblock.menu.button.impl.menu;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.island.PlayerRole;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.menu.button.SuperiorMenuButton;
import com.bgsoftware.superiorskyblock.menu.impl.MenuMemberRole;
import com.bgsoftware.superiorskyblock.utils.items.TemplateItem;
import com.bgsoftware.superiorskyblock.wrappers.SoundWrapper;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

public final class MemberRoleButton extends SuperiorMenuButton<MenuMemberRole> {

    private final PlayerRole playerRole;

    private MemberRoleButton(TemplateItem buttonItem, SoundWrapper clickSound, List<String> commands,
                             String requiredPermission, SoundWrapper lackPermissionSound,
                             PlayerRole playerRole) {
        super(buttonItem, clickSound, commands, requiredPermission, lackPermissionSound);
        this.playerRole = playerRole;
    }

    @Override
    public void onButtonClick(SuperiorSkyblockPlugin plugin, MenuMemberRole superiorMenu, InventoryClickEvent clickEvent) {
        SuperiorPlayer clickedPlayer = plugin.getPlayers().getSuperiorPlayer(clickEvent.getWhoClicked());
        SuperiorPlayer targetPlayer = superiorMenu.getTargetPlayer();

        if (playerRole.isLastRole()) {
            plugin.getCommands().dispatchSubCommand(clickedPlayer.asPlayer(), "transfer",
                    targetPlayer.getName());
        } else {
            plugin.getCommands().dispatchSubCommand(clickedPlayer.asPlayer(), "setrole",
                    targetPlayer.getName() + " " + playerRole);
        }
    }

    public static class Builder extends AbstractBuilder<Builder, MemberRoleButton, MenuMemberRole> {

        private PlayerRole playerRole;

        public Builder setPlayerRole(PlayerRole playerRole) {
            this.playerRole = playerRole;
            return this;
        }

        @Override
        public MemberRoleButton build() {
            return new MemberRoleButton(buttonItem, clickSound, commands, requiredPermission,
                    lackPermissionSound, playerRole);
        }

    }

}
