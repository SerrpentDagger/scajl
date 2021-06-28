package commands;

import java.util.ArrayList;

import commands.Command.RunnableCommand;

public class Process
{
	private final ArrayList<RunnableCommand> process = new ArrayList<RunnableCommand>();
	
	public Process add(RunnableCommand cmd)
	{
		process.add(cmd);
		return this;
	}
	
	public void run()
	{
		process.forEach((cmd) -> cmd.run());
	}
}
