package me.char321.sfadvancements.core.command;

import me.char321.sfadvancements.SFAdvancements;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

public class SaveCommand implements SubCommand {

    @Override
    public boolean onExecute(CommandSender sender, Command command, String label, String[] args) {
        try {
            SFAdvancements.getAdvManager().save();
            sender.sendMessage("Advancement progress saved.");
            return true;
        } catch(IOException e) {
            sender.sendMessage("An error occurred while saving advancement progress!");
            sender.sendMessage("Please check the console for details.");
            SFAdvancements.logger().log(Level.SEVERE, e, () -> "Could not save advancement progress");
            return false;
        }
    }

    @Override
    public @Nonnull String getCommandName() {
        return "save";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
