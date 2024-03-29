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

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Matcher;

import group.MixedPair;

public class Label
{
	public final int line;
	public final boolean isScoped, isAccessible, getsAccess;
	public final String name;
	
	public Label(String labelText, int line)
	{
		isScoped = labelText.startsWith(Scajl.SCOPED_LABEL);
		labelText = labelText.replaceFirst(Scajl.LABEL_REG, "").trim();
		MixedPair<boolean[], String> labelModsModless = Scajl.prefixModsFrom(labelText, Scajl.VALID_LABEL_MODS);
		boolean[] labelMods = labelModsModless.a();
		isAccessible = labelMods[0];
		getsAccess = labelMods[1];
		Matcher match = Scajl.LABEL_MODS_PATTERN.matcher(labelText);
		name = match.replaceFirst("");
		this.line = line;
	}
	
	public Label(String name, int line, boolean scoped, boolean accessible, boolean getsAccess)
	{
		this.name = name;
		this.line = line;
		this.isScoped = scoped;
		this.isAccessible = accessible;
		this.getsAccess = getsAccess;
	}
	
	@Override
	public String toString()
	{
		return name + ":" + line;
	}
	
	//////////////////////
	
	public static class LabelTree
	{
		private final HashMap<String, LabelTree> subs = new HashMap<String, LabelTree>(7);
		private final WeakReference<LabelTree> parent;
		public final Label root;
		private LabelTree growing = null;
		private boolean done = false;
		
		private LabelTree(Label root, LabelTree parent)
		{
			this.root = root;
			this.parent = new WeakReference<>(parent);
		}
		
		public LabelTree(Label root)
		{
			this(root, null);
		}
		
		public LabelTree open(Label into)
		{
			if (done)
				throw new IllegalStateException("Cannot re-open a closed LabelTree.");
			if (growing == null)
				growing = new LabelTree(into, this);
			else
				growing.open(into);
			return this;
		}
		
		public LabelTree close()
		{
			if (done)
				throw new IllegalStateException("This Label tree is already closed.");
			if (growing == null)
				done = true;
			else
			{
				growing.close();
				if (growing.done)
				{
					subs.put(growing.root.name, growing);
					growing = null;
				}
			}
			return this;
		}
		
		public boolean contains(String label)
		{
			return subs.containsKey(label);
		}
		
		public boolean contains(Label label)
		{
			return contains(label.name);
		}
		
		public LabelTree getFor(String label)
		{
			LabelTree parent = this;
			while (parent != null)
			{
				LabelTree got = parent.subs.get(label);
				if (got != null)
					return got;
				parent = parent.parent.get();
			}
			return null;
		}
		
		public LabelTree getFor(Label label)
		{
			return getFor(label.name);
		}
		
		public boolean accessibleHere(Label label)
		{
			return getFor(label) != null;
		}
		
		@Override
		public String toString()
		{
			String str = root + "{";
			Iterator<LabelTree> subIt = subs.values().iterator();
			while (subIt.hasNext())
				str += subIt.next().toString();
			return str + (done ? "}" : (growing != null ? growing.toString() : "") + " --");
		}
	}
}
