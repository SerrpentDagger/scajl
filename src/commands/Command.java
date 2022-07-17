package commands;

public class Command
{
	private boolean isVarArgs = false, isDisabled = false;
	boolean[] rawArg;
	boolean[] nullable;
	String name;
	String ret;
	String desc;
	CmdArg<?>[] args;
	CmdFunc func;
	
	protected Command(String name, String ret, String desc, CmdArg<?>... args)
	{
		this.name = name;
		this.args = args;
		this.ret = ret;
		this.desc = desc;
		rawArg = new boolean[args.length];
		nullable = new boolean[args.length];
	}
	
	public Command setFunc(CmdFunc func)
	{
		this.func = func;
		return this;
	}
	
	public Command setVarArgs()
	{
		isVarArgs = true;
		return this;
	}
	
	public boolean isVarArgs()
	{
		return isVarArgs;
	}
	
	public Command setDisabled(boolean disabled)
	{
		isDisabled = disabled;
		return this;
	}
	
	public boolean isDisabled()
	{
		return isDisabled;
	}
	
	public Command rawArg(int... indices)
	{
		for (int i : indices)
			rawArg[i] = true;
		return this;
	}
	
	public Command nullable(int... indices)
	{
		for (int i : indices)
			nullable[i] = true;
		return this;
	}
	
	public boolean rawToken(int arg, int tok)
	{
		return rawArg[arg] || args[arg].rawToken(tok);
	}
	
	public boolean nullableArg(int arg)
	{
		return nullable[arg];
	}
	
	public String getName()
	{
		return name;
	}
	
	public String getReturn()
	{
		return ret;
	}
	
	public String getDescription()
	{
		return desc;
	}
	
	public String getArgInfo()
	{
		String str = "";
		for (int i = 0; i < args.length; i++)
			str += args[i].getInfoString() + (i == args.length - 1 ? "" : ", ");
		
		if (isVarArgs)
			str += "...";
		
		return str;
	}
	
	public String getInfoString()
	{
		return this.getName() + " | Args: " + this.getArgInfo() + ", Return: " + this.getReturn() + ", Desc: " + this.getDescription();
	}
	
	public static class RunnableCommand
	{
		private final Object[] objs;
		private final String inputArgs;
		private final Command cmd;
		
		public RunnableCommand(Command cmd, String input, Object... args)
		{
			this.cmd = cmd;
			objs = args;
			inputArgs = input;
		}
		
		public String run(Script ctx)
		{
			return cmd.func.cmd(ctx, objs);
		}
		
		public String getInput()
		{
			return inputArgs;
		}
	}
	
	public static class CommandResult
	{
		public final String output;
		public final boolean shouldBreak;
		
		public CommandResult(String out, boolean brk)
		{
			output = out;
			shouldBreak = brk;
		}
	}
	
	@FunctionalInterface
	public static interface CmdFunc
	{
		public String cmd(Script ctx, Object... args);
	}
}
