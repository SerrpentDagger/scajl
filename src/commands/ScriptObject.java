package commands;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;

import commands.Script.CommandParseException;

public class ScriptObject<T>
{
	public final HashMap<String, T> objs = new HashMap<String, T>();
	
	private final String typeName;
	private final CmdArg<?>[] constArgs;
	private String description;
	private SOConstruct<T> constructor;
	private int unparseID = 0;
	
	private CmdArg<T> cmdArg;
	private final HashSet<ScriptObject<? extends T>> subs = new HashSet<>();
	private final LinkedHashMap<String, Command> memberCmds = new LinkedHashMap<>();
	
	////////////////////////////
	
	public ScriptObject(String typeName, String description, Class<T> cl, CmdArg<?>... constArgs)
	{
		this.typeName = typeName;
		this.description = description;
		this.constArgs = constArgs;
		
		cmdArg = new CmdArg<T>(typeName, cl)
		{
			@Override
			public T parse(String trimmed)
			{
				T obj = objs.get(trimmed), old = null;
				if (obj != null)
					return obj;
				for (ScriptObject<? extends T> sub : subs)
				{
					obj = sub.cmdArg.parse(trimmed);
					if (old != null && obj != null)
						throw new CommandParseException("The key '" + trimmed + "' exists in at least two subtypes of " + typeName);
					if (obj != null)
						old = obj;					
				}
				return old;
			}
			
			@Override
			public String unparse(T obj)
			{
				if (obj == null)
					return Script.NULL;
				if (objs.containsValue(obj))
				{
					for (Entry<String, T> ent : objs.entrySet())
						if (obj.equals(ent.getValue()))
								return ent.getKey();
				}
				String name = typeName + unparseID++;
				objs.put(name, obj);
				return name;
			};
		}.reg();
	}
	
	public void construct(Script ctx, String name, Object[] params)
	{
		if (constructor == null)
			throw new IllegalStateException("Null constructor for ScriptObject of type: " + typeName);
		objs.put(name, constructor.construct(ctx, name, params));
	}
	
	public boolean isObject(String trimmed)
	{
		return cmdArg.parse(trimmed) != null;
	}
	
	public T getObject(String trimmed)
	{
		return cmdArg.parse(trimmed);
	}
	
	public boolean destroy(String trimmed)
	{
		AtomicBoolean out = new AtomicBoolean(false);
		if (objs.remove(trimmed) != null)
			out.set(true);
		subs.forEach((sub) ->
		{
			if (sub.objs.remove(trimmed) != null)
				out.set(true);
		});
		return true;
	}
	
	//////////
	
	public static <SO> ScriptObject<SO> supOf(String type, String description, Class<SO> cl, ScriptObject<? extends SO>[] subs, CmdArg<?>... constArgs)
	{
		ScriptObject<SO> sup = new ScriptObject<SO>(type, description, cl, constArgs);
		for (ScriptObject<? extends SO> ext : subs)
			sup.subs.add(ext);
		return sup;
	}
	
	public static <SUP, SUB extends SUP> ScriptObject<SUB> subOf(String type, String description, Class<SUB> cl, ScriptObject<SUP> sup, CmdArg<?>... constArgs)
	{
		ScriptObject<SUB> sub = new ScriptObject<SUB>(type, description, cl, constArgs);
		sup.subs.add(sub);
		return sub;
	}
	
	///////////////////////////
	
	public Command add(String name, String ret, String desc, CmdArg<?>... args)
	{
		Command cmd = new Command(name, ret, desc, args);
		if (memberCmds.put(name, cmd) != null)
			throw new IllegalArgumentException("Cannot register two commands to the same name in the same type: " + typeName + Script.MEMBER_ACCESS + name);
		return cmd;
	}
	
	public ScriptObject<T> setConstructor(SOConstruct<T> construct)
	{
		this.constructor = construct;
		return this;
	}
	
	public String getDescription()
	{
		return description;
	}
	
	public Command[] getMemberCommands()
	{
		return memberCmds.values().toArray(new Command[memberCmds.size()]);
	}
	
	public LinkedHashMap<String, Command> getMemberCommandMap()
	{
		return memberCmds;
	}
	
	public String getTypeName()
	{
		return typeName;
	}
	
	public String getCommandName()
	{
		return typeName.toLowerCase();
	}
	
	public String getInfoString()
	{
		return this.getTypeName() + " | Desc: " + this.getDescription();
	}
	
	public CmdArg<?>[] getConstArgs()
	{
		return constArgs;
	}
	
	public CmdArg<T> argOf()
	{
		return cmdArg;
	}
	
	public <SUB extends T> ScriptObject<SUB> getSub(String subtype)
	{
		Iterator<ScriptObject<? extends T>> it = subs.iterator();
		while (it.hasNext())
		{
			@SuppressWarnings("unchecked")
			ScriptObject<SUB> n = (ScriptObject<SUB>) it.next();
			if (n.typeName.equals(subtype))
				return n;
		}
		return null;
	}
	
	public Command getMemberCmd(String name)
	{
		return memberCmds.get(name);
	}
	
	///////////////////////
	
	public interface SOConstruct<T>
	{
		public T construct(Script ctx, String name, Object[] objs);
	}
}
