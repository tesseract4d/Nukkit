package cn.nukkit.command.defaults;

import cn.nukkit.ServerInfo;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.TranslationContainer;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class PardonCommand extends VanillaCommand {

    public PardonCommand(String name) {
        super(name, "%nukkit.command.unban.player.description", "%commands.unban.usage");
        this.setPermission("nukkit.command.unban.player");
        this.setAliases(new String[]{"unban"});
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(new TranslationContainer("commands.generic.usage", this.usageMessage));

            return false;
        }

        ServerInfo.banByName.remove(args[0]);

        Command.broadcastCommandMessage(sender, new TranslationContainer("%commands.unban.success", args[0]));

        return true;
    }
}
