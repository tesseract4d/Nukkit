package cn.nukkit.command.defaults;

import cn.nukkit.ServerProperties;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.TranslationContainer;

/**
 * Created on 2015/11/13 by xtypr.
 * Package cn.nukkit.command.defaults in project Nukkit .
 */
public class SaveOnCommand extends VanillaCommand {

    public SaveOnCommand(String name) {
        super(name, "%nukkit.command.saveon.description", "%commands.save-on.usage");
        this.setPermission("nukkit.command.save.enable");
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }
        ServerProperties.auto_save = true;
        Command.broadcastCommandMessage(sender, new TranslationContainer("commands.save.enabled"));
        return true;
    }
}
