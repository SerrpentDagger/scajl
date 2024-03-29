/**
 * This file is part of Scajl, which is a scripting language for Java applications.
 * Copyright (c) 2023, SerpentDagger (MRRH) <serpentdagger.contact@gmail.com>.
 * 
 * Scajl is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 * 
 * Scajl is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along with Scajl.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package commands;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;

import annotations.ScajlClone;
import commands.CmdArg.ObjConstruct;
import commands.ScajlVariable.SVJavObj;
import group.MixedPair;
import utilities.StringUtils;

public class ScriptObject<T>
{
	private final String typeName;
	private String description;
	private int unparseID = 0;
	
	private int argRegID = 0;
	private CmdArg<T> cmdArg;
	private CmdArg<T>[] inlineConst;
	private final HashSet<ScriptObject<? extends T>> subs = new HashSet<>();
	private final LinkedHashMap<String, Command> memberCmds = new LinkedHashMap<>();
	
	////////////////////////////
	
	@SuppressWarnings("unchecked")
	public ScriptObject(String typeName, String description, Class<T> cl)
	{
		this.typeName = typeName;
		this.description = description;
		inlineConst = (CmdArg<T>[]) Array.newInstance(CmdArg.class, 0);
		
		cmdArg = new CmdArg<T>(typeName, cl)
		{
			@Override
			public T parse(ScajlVariable[] vars, int off, Scajl ctx)
			{
				if (!(vars[off] instanceof SVJavObj))
					return null;
				SVJavObj jObj = (SVJavObj) vars[off];
				Object[] types = jObj.value;
				for (Object obj : types)
					if (cl.isAssignableFrom(obj.getClass()))
						return (T) obj;
				return null;
			}
		}.reg();
	}
	
	public String newObjKey()
	{
		return typeName + unparseID++;
	}
	
	public String newArgRegID()
	{
		return typeName + argRegID++;
	}
	
	public boolean isObject(ScajlVariable var, Scajl ctx)
	{
		return cmdArg.parse(var, ctx) != null;
	}
	
	public T getObject(ScajlVariable var, Scajl ctx)
	{
		return cmdArg.parse(var, ctx);
	}
	
	//////////
	
	public static <SO> ScriptObject<SO> supOf(String type, String description, Class<SO> cl, ScriptObject<? extends SO>[] subs)
	{
		ScriptObject<SO> sup = new ScriptObject<SO>(type, description, cl);
		for (ScriptObject<? extends SO> ext : subs)
			sup.subs.add(ext);
		return sup;
	}
	
	public static <SUP, SUB extends SUP> ScriptObject<SUB> subOf(String type, String description, Class<SUB> cl, ScriptObject<SUP> sup)
	{
		ScriptObject<SUB> sub = new ScriptObject<SUB>(type, description, cl);
		sup.subs.add(sub);
		return sub;
	}
	
	public static <SUP, SUB extends SUP> ScriptObject<SUB> makeSub(ScriptObject<SUB> sub, ScriptObject<SUP> sup)
	{
		sup.subs.add(sub);
		return sub;
	}
	
	public static <SUP> ScriptObject<SUP> makeSup(ScriptObject<SUP> sup, ScriptObject<? extends SUP>[] subs)
	{
		for (ScriptObject<? extends SUP> ext : subs)
			sup.subs.add(ext);
		return sup;
	}
	
	@SuppressWarnings("unchecked")
	public static <SUP, SUB extends SUP> MixedPair<ScriptObject<SUP>, ScriptObject<SUB>> castHirearchy(ScriptObject<?> sup, ScriptObject<?> sub)
	{
		return new MixedPair<>((ScriptObject<SUP>) sup, (ScriptObject<SUB>) sub);
	}
	
	///////////////////////////
	
	public Command add(String name, String ret, String desc, CmdArg<?>... args)
	{
		Command cmd = new Command(name, ret, desc, args);
		if (memberCmds.put(name, cmd) != null)
			throw new IllegalArgumentException("Cannot register two commands to the same name in the same type: " + typeName + Scajl.MEMBER_ACCESS + name);
		return cmd;
	}
	
	public CmdArg<T> add(CmdArg<T> inline)
	{
		LinkedHashMap<Integer, CmdArg<?>> bin = CmdArg.ARGS.get(cmdArg.cls);
		if (bin != null && bin.get(inline.tokenCount()) != inline)
			throw new IllegalArgumentException("Cannot register more than one CmdArg for the same token count. Use a PrefCmdArg for this.");
		
		inlineConst = Arrays.copyOf(inlineConst, inlineConst.length + 1);
		inlineConst[inlineConst.length - 1] = inline;
		inline.reg();

		return inline;
	}
	
	public CmdArg<T> add(ObjConstruct<T> construct, CmdArg<?>... args)
	{
		CmdArg<T> inline = CmdArg.inlineOf(construct, cmdArg, args);
		
		return add(inline);
	}
	
	public void setDescription(String str)
	{
		description = str;
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
		String inf = (ScajlClone.isSC(cmdArg.cls) ? Scajl.RAW_CONTENTS : "") + getTypeName() + " | Inline formats: ";
		CmdArg<?>[] inline = CmdArg.ARGS.get(cmdArg.cls).values().toArray(new CmdArg<?>[0]);
		inf += inline.length > 0 ? StringUtils.toString(inline, (arg) -> arg.getInfoString(), "'", "', '", "'") : "None";
		inf += ", Desc: " + this.getDescription();
		return inf;
	}
	
	public String hirearchyString()
	{
		return hirearchyString(0);
	}
	private String hirearchyString(int level)
	{
		String str = StringUtils.mult("   ", level) + "~ " +  getInfoString();
		if (subs.isEmpty())
			return str;
		Iterator<ScriptObject<? extends T>> it = subs.iterator();
		while (it.hasNext())
			str += "\n" + it.next().hirearchyString(level + 1);
		return str;
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
		Command mem = memberCmds.get(name);
		if (mem == null)
		{
			Class<?> sup = cmdArg.cls.getSuperclass();
			while (sup != null)
			{
				ScriptObject<?> supSO = Scajl.getType(sup);
				if (supSO != null)
					return supSO.getMemberCmd(name);
				else
					sup = sup.getSuperclass();
			}
		}
		return mem;
	}
}
