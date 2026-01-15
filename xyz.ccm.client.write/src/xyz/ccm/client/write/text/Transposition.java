/*
 * Copyright (c) 2017..2026 Jérôme Desquilbet <jeromede@fr.ibm.com>
 *
 * Permission to use, copy, modify, and distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
*/

package xyz.ccm.client.write.text;

import java.util.Map;

public class Transposition {

	public static String transpose(String content, Map<String, String> tasks) {
		String result = "";
		String id = null;
		String newId;
		boolean inId = false;
		for (char c : content.toCharArray()) {
			if ('0' <= c && c <= '9') {
				if (inId) {
					id = id.concat("" + c);
					// System.out.println("2) c: '" + c + "' id: \"" + id + "\"
					// result: \"" + result + "\"");
				} else {
					inId = true;
					id = "" + c;
				}
			} else if (inId) {
				inId = false;
				newId = tasks.get(id);
				if (null == newId) {
					result = result.concat(id);
				} else {
					result = result.concat(newId + ' ' + '{' + id + '}');
				}
				result = result.concat("" + c);
			} else {
				result = result.concat("" + c);
			}
		}
		if (inId) {
			newId = tasks.get(id);
			if (null == newId) {
				result = result.concat(id);
			} else {
				result = result.concat(newId + ' ' + '{' + id + '}');
			}
		}
		return result;
	}

	public static String prefix(String content, int id) {
		return /* "{" + id + "} + */ content;
	}

	public static String prefix(String content, String id) {
		return /* "{" + id + "} " + */ content;
	}

}
