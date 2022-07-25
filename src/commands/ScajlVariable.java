package commands;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import arrays.AUtils;
import arrays.AUtils.Ind;
import mod.serpentdagger.artificialartificing.utils.group.MixedPair;
import utilities.StringUtils;

public abstract class ScajlVariable
{
	public static final SVVal NULL = new SVVal(Script.NULL);
	
	///////////////////////////
	
	public final String input, modless;
	protected WeakReference<SVMember> selfCtx;
	
	public ScajlVariable(String input, String modless, SVMember selfCtx)
	{
		this.input = input;
		this.modless = modless;
		this.selfCtx = new WeakReference<>(selfCtx);
	}
	
	public abstract String val(Script ctx);
	public abstract String raw();
	public VarCtx varCtx(String[] memberAccess, int off, boolean put, Script ctx, SVMember selfCtx)
	{
		if (put || memberAccess != null && off != memberAccess.length)
			ctx.parseExcept("Invalid member access", "The indexed variable is not a type which can be indexed.", "From access: " + StringUtils.toString(memberAccess, "", "" + Script.ARR_ACCESS, ""));
		return new VarCtx(() -> this);
	}
	
	@Override
	public String toString()
	{
		return raw();
	}
	
/*	public ScajlVariable[] splitTokens(IntPredicate rawDefault, Script ctx)
	{
		String[] split = Script.tokensOf(input);
		ScajlVariable[] out = new ScajlVariable[split.length];
		if (out.length != 1)
			for (int i = 0; i < out.length; i++)
				out[i] = getVar(split[i], rawDefault.test(i), ctx);
		else
			out[0] = this;
		return out;
	}*/
	
	@Override
	public abstract ScajlVariable clone();
	//////////////////////
	
	public static class SVVal extends ScajlVariable
	{
		public SVVal(String input, String modless)
		{
			super(input, modless, null);
		}
		
		public SVVal(String inputModless)
		{
			this(inputModless, inputModless);
		}
		
		public SVVal(double val)
		{
			this("" + val);
		}
		
		@Override
		public String val(Script ctx)
		{
			return modless;
		}

		@Override
		public String raw()
		{
			return modless;
		}

		@Override
		public SVVal clone()
		{
			return new SVVal(input, modless);
		}
	}
	
	public static class SVRef extends ScajlVariable
	{
		public SVRef(String input, String modless)
		{
			super(input, modless, null);
		}
		
		@Override
		public String val(Script ctx)
		{
			ScajlVariable var = ctx.scope.get(modless);
			return var == null ? NULL.input : var.val(ctx);
		}
		
		@Override
		public String raw()
		{
			return Script.REF + modless;
		}
		
		@Override
		public SVRef clone()
		{
			return new SVRef(input, modless);
		}
	}
	
	public static class SVString extends ScajlVariable
	{
		public final String unraw;
		
		public SVString(String input, String modless)
		{
			super(input, modless, null);
			unraw = Script.stringTrim(modless);
		}
		
		@Override
		public String val(Script ctx)
		{
			return unraw;
		}
		
		@Override
		public String raw()
		{
			return Script.STRING_CHAR + unraw + Script.STRING_CHAR;
		}
		
		@Override
		public SVString clone()
		{
			return new SVString(input, modless);
		}
	}
	
	protected static abstract class SVMember extends ScajlVariable
	{
		public final String name;
		
		public SVMember(String input, String modless, String name, SVMember selfCtx)
		{
			super(input, modless, selfCtx);
			this.name = name;
		}
		
		@Override
		public String val(Script ctx)
		{
			return name == null ? raw() : raw();
		}
		
		@Override
		public abstract VarCtx varCtx(String[] memberAccess, int off, boolean put, Script ctx, SVMember selfCtx);
	}
	
	public static class SVMap extends SVMember
	{
		private LinkedHashMap<String, ScajlVariable> map;
		
		public SVMap(String input, String modless, String name, Script ctx, SVMember selfCtx)
		{
			super(input, modless, name, selfCtx);
			String[] elements = Script.arrayElementsOf(modless);
			map = new LinkedHashMap<>(elements.length);
			for (int i = 0; i < elements.length; i++)
			{
				String[] keyVal = Script.syntaxedSplit(elements[i], Script.MAP_KEY_EQ);
				String key = getVar(keyVal[0], true, ctx, this).val(ctx);
				map.put(key, getVar(keyVal[1], false, ctx, this));
			}
		}
		public SVMap(String input, String modless, String name, LinkedHashMap<String, ScajlVariable> map, SVMember selfCtx)
		{
			super(input, modless, name, selfCtx);
			this.map = map;
		}

		@Override
		public VarCtx varCtx(String[] memberAccess, int off, boolean put, Script ctx, SVMember selfCtx)
		{
			if (off == memberAccess.length - 1)
			{
				if (memberAccess[off].equals(Script.ARR_LEN))
					return new VarCtx(() -> new SVVal(map.size()));
				else
					return new VarCtx(() ->
					{
						if (!map.containsKey(memberAccess[off]))
							return NULL;
						return map.get(memberAccess[off]);
					}, (var) ->
					{
						map.put(memberAccess[off], var);
						var.selfCtx = new WeakReference<>(this);
					});
			}
			if (memberAccess[off].equals(Script.ARR_SELF))
				return selfCtx.varCtx(memberAccess, off + 1, put, ctx, this);
			if (!map.containsKey(memberAccess[off]))
				ctx.parseExcept("Invalid Map key for continued indexing: " + memberAccess[off], "The specified key is missing.", "From access: " + StringUtils.toString(memberAccess, "", "" + Script.ARR_ACCESS, ""));
			return map.get(memberAccess[off]).varCtx(memberAccess, off + 1, put, ctx, this);
		}

		@Override
		public String raw()
		{
			String out = "" + Script.ARR_S;
			Iterator<Entry<String, ScajlVariable>> it = map.entrySet().iterator();
			while (it.hasNext())
			{
				Entry<String, ScajlVariable> ent = it.next();
				out += ent.getKey() + Script.MAP_KEY_EQ + ent.getValue().raw() + (it.hasNext() ? Script.ARR_SEP + " " : "");
			}
			return out + Script.ARR_E;
		}

		@Override
		public ScajlVariable clone()
		{
			LinkedHashMap<String, ScajlVariable> deepCopy = new LinkedHashMap<>();
			SVMap clone = new SVMap(input, modless, null, deepCopy, selfCtx.get());
			Iterator<Entry<String, ScajlVariable>> it = map.entrySet().iterator();
			while (it.hasNext())
			{
				Entry<String, ScajlVariable> ent = it.next();
				ScajlVariable cop;
				deepCopy.put(ent.getKey(), cop = ent.getValue().clone());
				cop.selfCtx = new WeakReference<>(clone);
			}
			return clone;
		}
	}
	
	public static class SVTokGroup extends SVMember
	{
		private ScajlVariable[] array;

		public SVTokGroup(String input, String modless, Script ctx)
		{
			super(input, modless, null, null);
			String[] elements = Script.tokensOf(Script.unpack(modless));
			array = new ScajlVariable[elements.length];
			for (int i = 0; i < elements.length; i++)
				array[i] = getVar(elements[i], false, ctx);
		}
		public SVTokGroup(String input, String modless, ScajlVariable[] array)
		{
			super(input, modless, null, null);
			this.array = array;
		}
		
		@Override
		public String raw()
		{
			String out = "" + Script.TOK_S;
			for (int i = 0; i < array.length; i++)
				out += array[i].raw() + (i == array.length - 1 ? "" : " ");
			return out + Script.TOK_E;
		}
		
		@Override
		public SVTokGroup clone()
		{
			return new SVTokGroup(input, modless, Arrays.copyOf(array, array.length));
		}
		@Override
		public VarCtx varCtx(String[] memberAccess, int off, boolean put, Script ctx, SVMember selfCtx)
		{
			if (put || memberAccess != null && off != memberAccess.length)
				ctx.parseExcept("Invalid member access on Token Group", "The indexed variable is not a type which can be indexed.", "From access: " + StringUtils.toString(memberAccess, "", "" + Script.ARR_ACCESS, ""));
			return new VarCtx(() -> this);
		}
	}
	
	public static class SVArray extends SVMember
	{
		private ScajlVariable[] array;
		private SVVal length;
		
		public SVArray(String input, String modless, String name, Script ctx, SVMember selfCtx)
		{
			super(input, modless, name, selfCtx);
			String[] elements = Script.arrayElementsOf(modless);
			array = new ScajlVariable[elements.length];
			Arrays.fill(array, NULL);
			for (int i = 0; i < elements.length; i++)
				array[i] = getVar(elements[i], false, ctx, this);
			length = new SVVal(array.length);
		}
		public SVArray(String input, String modless, String name, ScajlVariable[] array, SVMember selfCtx)
		{
			super(input, modless, name, selfCtx);
			this.array = array;
			length = new SVVal(array.length);
		}
		public SVArray(String name, ScajlVariable[] array, SVMember selfCtx)
		{
			this(name, name, name, array, selfCtx);
		}

		@Override
		public String raw()
		{
			String out = "" + Script.ARR_S;
			for (int i = 0; i < array.length; i++)
				out += array[i].raw() + (i == array.length - 1 ? "" : Script.ARR_SEP + " ");
			return out + Script.ARR_E;
		}
		
		@Override
		public VarCtx varCtx(String[] memberAccess, int off, boolean put, Script ctx, SVMember selfCtx)
		{
			if (memberAccess[off].equals(Script.ARR_LEN) && off == memberAccess.length - 1)
				return new VarCtx(() -> length, (var) ->
				{
					Integer len = CmdArg.INT.parse(var.val(ctx));
					if (len == null)
						ctx.parseExcept("Invalid token resolution for Array length", "Array lengths must be specified as numbers.");
					int oldLen = array.length;
					array = Arrays.copyOf(array, len);
					if (len > oldLen)
						Arrays.fill(array, oldLen, len, NULL);
					length = new SVVal(len);
				});
			else
			{
				if (memberAccess[off].equals(Script.ARR_SELF))
					return varCtx(memberAccess, off + 1, put, ctx, this);
				Integer ind = CmdArg.INT.parse(memberAccess[off]);
				if (ind == null)
					ctx.parseExcept("Invalid Array index: " + memberAccess[off], "Array indices must be numbers.", "From access: " + StringUtils.toString(memberAccess, "", "" + Script.ARR_ACCESS, ""));
				if (ind >= array.length)
					ctx.parseExcept("Invalid Array index: " + ind, "Index out of bounds.", "From access: " + StringUtils.toString(memberAccess, "", "" + Script.ARR_ACCESS, ""));
				if (off == memberAccess.length - 1)
					return new VarCtx(() -> array[ind], (var) ->
					{
						array[ind] = var;
						var.selfCtx = new WeakReference<>(this);
					});
				return array[ind].varCtx(memberAccess, off + 1, put, ctx, this);
			}
		}
		
		@Override
		public SVArray clone()
		{
			ScajlVariable[] deepCopy = Arrays.copyOf(array, array.length);
			SVArray clone = new SVArray(input, modless, null, deepCopy, selfCtx.get());
			for (int i = 0; i < deepCopy.length; i++)
			{
				deepCopy[i] = deepCopy[i].clone();
				deepCopy[i].selfCtx = new WeakReference<>(clone);
			}
			return clone;
		}
	}
	
	public static class SVExec extends ScajlVariable
	{
		public SVExec(String input, String modless, SVMember selfCtx)
		{
			super(input, modless, selfCtx);
		}

		@Override
		public String val(Script ctx)
		{
			return ctx.runExecutable(modless, selfCtx.get()).output;
		}

		@Override
		public String raw()
		{
			return Script.REF + modless;
		}

		@Override
		public SVExec clone()
		{
			return new SVExec(input, modless, selfCtx.get());
		}
	}
	
	////////////////////////
	
	public static void putVar(String name, ScajlVariable var, Script ctx)
	{
		putVar(name, var, ctx, null);
	}
	public static void putVar(String name, ScajlVariable var, Script ctx, SVMember selfCtx)
	{
		String[] arrAcc = Script.syntaxedSplit(name, "" + Script.ARR_ACCESS);
		ScajlVariable toVar = getVar(arrAcc[0], arrAcc.length == 1, ctx);
		if (arrAcc.length > 1)
		{
			String[] access = new String[arrAcc.length];
			for (int i = 0; i < access.length; i++)
				if (i > 0)
					access[i] = getVar(arrAcc[i], false, ctx).val(ctx);
			toVar.varCtx(access, 1, true, ctx, selfCtx).put.accept(var);
		}
		else
		{
			name = toVar.input;
			if (Script.ILLEGAL_VAR_MATCHER.matcher(name).matches())
				ctx.parseExcept("Illegal characters in variable name", name);
			try
			{
				Double.parseDouble(name);
				ctx.parseExcept("Numerical variable name", name);
			}
			catch (NumberFormatException e)
			{}
			ctx.scope.put(name, var);
		}
	}
	
	public static boolean isVar(String input, Script ctx)
	{
		if (input.equals(Script.NULL))
			return false;
		
		MixedPair<boolean[], String> modPair = Script.prefixModsFrom(input, Script.VALID_VAR_MODS);
		String modless = modPair.b();
		boolean[] mods = modPair.a();
		if (mods[1])
			return false;
		ScajlVariable var = ctx.scope.get(modless);
		return var != null;
	}
	
	public static String[] preParse(String token, Script ctx)
	{
		return preParse(new String[] { token }, ctx);
	}
	
	public static String[] preParse(String[] tokens, Script ctx)
	{
		Ind ind = new Ind(0);
		for (int i = 0; i < tokens.length; i = ind.get())
		{
			if (tokens[i].charAt(0) == Script.UNPACK)
			{
				String unp = getVar(tokens[i].substring(1), false, ctx).val(ctx);
				unp = Script.unpack(unp);
				tokens = AUtils.replace(tokens, Script.tokensOf(unp), i, ind);
			}
			else
				ind.inc();
		}
		return tokens;
	}
	
	public static ScajlVariable getVar(String input, boolean rawDef, Script ctx)
	{
		return getVar(input, rawDef, ctx, null);
	}
	public static ScajlVariable getVar(String input, boolean rawDef, Script ctx, SVMember selfCtx)
	{
		if (input.equals(Script.NULL))
			return NULL;
		
		boolean isNumber = CmdArg.DOUBLE.parse(input) != null;
		if (isNumber)
			return new SVVal(input);
		MixedPair<boolean[], String> modPair = Script.prefixModsFrom(input, Script.VALID_VAR_MODS);
		String modless = modPair.b();
		boolean[] mods = modPair.a();
		boolean isUnraw = mods[0];
		boolean isRaw = mods[1] || (rawDef && !isUnraw);
		boolean isRef = mods[2];
		boolean isRawCont = mods[3];
		boolean isUnpack = mods[4] || mods[5];
		if (isUnpack)
			ctx.parseExcept("Illegal reference modifier.", "The 'unpack' reference modifier is disallowed for this location.", input);
		int modCount = countTrues(mods);
		if (modCount > 1)
			ctx.parseExcept("Invalid variable usage", "A maximum of 1 reference modifier is allowed per token");
		if (!isRaw)
		{
			String[] arrAcc = Script.syntaxedSplit(input, "" + Script.ARR_ACCESS);
			if (arrAcc.length > 1)
			{
				String[] access = new String[arrAcc.length];
				ScajlVariable var = null;
				for (int i = 0; i < access.length; i++)
				{
					if (i > 0)
						access[i] = getVar(arrAcc[i], false, ctx, selfCtx).val(ctx);
					else
						var = getVar(arrAcc[0], false, ctx, selfCtx);
				}
				
				return var.varCtx(access, 1, false, ctx, selfCtx).get.get();
			}
		}
		
		boolean isString = modless.startsWith("" + Script.STRING_CHAR);
		if (isString && !modless.endsWith("" + Script.STRING_CHAR))
			ctx.parseExcept("Malformed String", "A quoted String must start and end with the '\"' character.");
		boolean isContainer = !isString && modless.startsWith("" + Script.ARR_S);
		boolean hasEq = isContainer && Script.syntaxedContains(modless, Pattern.quote("" + Script.MAP_KEY_EQ), 1);
		if (isContainer && !modless.endsWith("" + Script.ARR_E))
			ctx.parseExcept("Malformed Container", "An Array or Map must start and end with the '" + Script.ARR_S + "' and '" + Script.ARR_E + "' characters, respectively.");
		boolean isArray = isContainer && !hasEq;
		boolean isMap = isContainer && hasEq;
		if ((isArray || isMap || isString) && modCount > 0)
			ctx.parseExcept("Invalid value usage", "A quoted String or an Array or Map must start and end with their respective boxing characters.");
			
		boolean isGroup = !isContainer && modless.startsWith("" + Script.TOK_S);
		if (isGroup && !modless.endsWith("" + Script.TOK_E))
			ctx.parseExcept("Malformed Token Group", "A Token Group must start and end with the '" + Script.TOK_S + "' and '" + Script.TOK_E + "' characters, respectively.");
		
		boolean isExec = !isGroup && modless.startsWith("" + Script.SCOPE_S);
		if (isExec && !modless.endsWith("" + Script.SCOPE_E))
			ctx.parseExcept("Malformed Executable", "An Executable must start and end with the '" + Script.SCOPE_S + "' and '" + Script.SCOPE_E + "' characters, respectively.");
		
		if (isExec && (isRaw || isRawCont || isUnraw))
			ctx.parseExcept("Invalid executable modifier", "An Executable can only be modified with the 'reference' reference modifier '" + Script.REF + "'.");
		
		if (isExec && !isRef)
			return getVar(ctx.runExecutable(modless, selfCtx).output, false, ctx, selfCtx);
		
		if (isString)
			return new SVString(input, modless);
		if (isArray)
			return new SVArray(input, modless, null, ctx, selfCtx);
		if (isGroup)
			return new SVTokGroup(input, modless, ctx);
		if (isMap)
			return new SVMap(input, modless, null, ctx, selfCtx);
		if (isRaw)
			return new SVVal(input, modless);
		if (isRef)
		{
			if (isExec)
				return new SVExec(input, modless, selfCtx);
			if (Script.ILLEGAL_VAR_MATCHER.matcher(modless).matches())
				ctx.parseExcept("Illegal characters in reference name", "The reference name must be a legal variable name.");
			return new SVRef(input, modless);
		}
		if (modless.equals(Script.ARR_SELF))
		{
			if (selfCtx != null)
				return selfCtx;
			else
				ctx.parseExcept("Illegal usage of the 'self' keyword.", "'self' can only be used within Container types.");
		}
		
		
		ScajlVariable var = ctx.scope.get(modless);
		if (var == null)
			return new SVVal(input, modless);
		if (isRawCont)
			return var.clone();
		return var;
	}
	
	private static int countTrues(boolean[] in)
	{
		int b = 0;
		for (int i = 0; i < in.length; i++)
			b += in[i] ? 1 : 0;
		return b;
	}
	
	///////////////////////////
	
	public static class VarCtx
	{
		public final Supplier<ScajlVariable> get;
		public final Consumer<ScajlVariable> put;
		
		public VarCtx(Supplier<ScajlVariable> get)
		{
			this(get, null);
		}
		public VarCtx(Supplier<ScajlVariable> get, Consumer<ScajlVariable> put)
		{
			this.put = put;
			this.get = get;
		}
	}
}
