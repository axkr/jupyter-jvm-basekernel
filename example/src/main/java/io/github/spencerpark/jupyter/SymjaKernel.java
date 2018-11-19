package io.github.spencerpark.jupyter;

import java.io.StringWriter;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import org.matheclipse.core.basic.Config;
import org.matheclipse.core.convert.AST2Expr;
import org.matheclipse.core.eval.EvalEngine;
import org.matheclipse.core.eval.ExprEvaluator;
import org.matheclipse.core.eval.exception.AbortException;
import org.matheclipse.core.eval.exception.Validate;
import org.matheclipse.core.expression.F;
import org.matheclipse.core.form.output.OutputFormFactory;
import org.matheclipse.core.interfaces.IExpr;
import org.matheclipse.parser.client.SyntaxError;
import org.matheclipse.parser.client.math.MathException;

import io.github.spencerpark.jupyter.kernel.BaseKernel;
import io.github.spencerpark.jupyter.kernel.LanguageInfo;
import io.github.spencerpark.jupyter.kernel.ReplacementOptions;
import io.github.spencerpark.jupyter.kernel.display.DisplayData;
import io.github.spencerpark.jupyter.kernel.util.CharPredicate;
import io.github.spencerpark.jupyter.kernel.util.SimpleAutoCompleter;
import io.github.spencerpark.jupyter.kernel.util.StringSearch;

public class SymjaKernel extends BaseKernel {

	private final ExprEvaluator fEvaluator;

	private final OutputFormFactory fOutputFactory;

	static {
		// distinguish between lower- and uppercase identifiers
		Config.PARSER_USE_LOWERCASE_SYMBOLS = false;
		Config.FILESYSTEM_ENABLED = true;
		F.initSymbols(null, null, true);
	}

	private static final SimpleAutoCompleter autoCompleter = SimpleAutoCompleter.builder().preferLong()
			// Keywords from a great poem at https://stackoverflow.com/a/12114140
			.withKeywords(AST2Expr.DOLLAR_STRINGS)
			.withKeywords(AST2Expr.SYMBOL_STRINGS)
			.withKeywords(AST2Expr.FUNCTION_STRINGS)
			.build();

	private static final CharPredicate idChar = CharPredicate.builder().inRange('a', 'z').inRange('A', 'Z').match('_')
			.build();

	private final LanguageInfo languageInfo;

	public SymjaKernel() {

		EvalEngine engine = new EvalEngine(false);
		fEvaluator = new ExprEvaluator(engine, false, 100);
		fEvaluator.getEvalEngine().setFileSystemEnabled(true);
		DecimalFormatSymbols usSymbols = new DecimalFormatSymbols(Locale.US);
		DecimalFormat decimalFormat = new DecimalFormat("0.0####", usSymbols);
		fOutputFactory = OutputFormFactory.get(false, false, decimalFormat);
		this.languageInfo = new LanguageInfo.Builder("symjamma")
				.version("1.0.0") //
				.mimetype("text/x-mathematica")//
				.fileExtension(".m")//
				.pygments("mathematica")//
				.codemirror("Mathematica")//
				.build();
	}

	@Override
	public LanguageInfo getLanguageInfo() {
		return languageInfo;
	}

	@Override
	public DisplayData eval(String expr) throws Exception {
//		ScriptContext ctx = engine.getContext();
//
//		// Redirect the streams
//		ctx.setWriter(new OutputStreamWriter(System.out));
//		ctx.setErrorWriter(new OutputStreamWriter(System.err));
//		ctx.setReader(new InputStreamReader(System.in));

		// Evaluate the code
		Object res = interpreter(expr);

		// If the evaluation returns a non-null value (the code is an expression like
		// 'a + b') then the return value should be this result as text. Otherwise
		// return null for nothing to be emitted for 'Out[n]'. Side effects may have
		// still printed something
		return res != null ? new DisplayData(res.toString()) : null;
	}

	@Override
	public DisplayData inspect(String code, int at, boolean extraDetail) throws Exception {
		StringSearch.Range match = StringSearch.findLongestMatchingAt(code, at, idChar);
		String id = "";
		Object val = null;
//		if (match != null) {
//			id = match.extractSubString(code);
//			val = this.engine.getContext().getAttribute(id);
//		}

		return new DisplayData(val == null ? "No memory value for '" + id + "'" : val.toString());
	}

	@Override
	public ReplacementOptions complete(String code, int at) throws Exception {
		StringSearch.Range match = StringSearch.findLongestMatchingAt(code, at, idChar);
		if (match == null)
			return null;
		String prefix = match.extractSubString(code);
		return new ReplacementOptions(autoCompleter.autocomplete(prefix), match.getLow(), match.getHigh());
	}
	
	/**
	 * Evaluates the given string-expression and returns the result in <code>OutputForm</code>
	 * 
	 * @param inputExpression
	 * @return
	 */
	public String interpreter(final String inputExpression) {
		IExpr result;
		final StringWriter buf = new StringWriter();
		try {
				result = fEvaluator.eval(inputExpression);
			if (result != null) {
				StringBuilder strBuffer = new StringBuilder();
				fOutputFactory.reset();
				fOutputFactory.convert(strBuffer, result);
				return strBuffer.toString();
			}
		} catch (final AbortException re) {
			return F.$Aborted.getSymbolName();
		} catch (final SyntaxError se) {
			String msg = se.getMessage();
			System.err.println(msg);
			System.err.println();
			System.err.flush();
			return "";
		} catch (final RuntimeException re) {
			Throwable me = re.getCause();
			if (me instanceof MathException) {
				Validate.printException(buf, me);
			} else {
				Validate.printException(buf, re);
			}
			System.err.println(buf.toString());
			System.err.flush();
			return "";
		} catch (final Exception e) {
			Validate.printException(buf, e);
			System.err.println(buf.toString());
			System.err.flush();
			return "";
		} catch (final OutOfMemoryError e) {
			Validate.printException(buf, e);
			System.err.println(buf.toString());
			System.err.flush();
			return "";
		} catch (final StackOverflowError e) {
			Validate.printException(buf, e);
			System.err.println(buf.toString());
			System.err.flush();
			return "";
		}
		return buf.toString();
	}
}
