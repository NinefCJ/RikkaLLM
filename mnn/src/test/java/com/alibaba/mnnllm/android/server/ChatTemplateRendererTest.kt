package com.alibaba.mnnllm.android.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTemplateRendererTest {

    private fun msg(role: String, content: String) = ChatTemplateRenderer.Message(role, content)

    @Test
    fun `renders ChatML template`() {
        val tpl = "{% for message in messages %}{% if message.role == 'system' %}" +
            "{{ '<|im_start|>system\\n' + message.content + '<|im_end|>\\n' }}" +
            "{% elif message.role == 'user' %}" +
            "{{ '<|im_start|>user\\n' + message.content + '<|im_end|>\\n' }}" +
            "{% elif message.role == 'assistant' %}" +
            "{{ '<|im_start|>assistant\\n' + message.content + '<|im_end|>\\n' }}" +
            "{% endif %}{% endfor %}{% if add_generation_prompt %}" +
            "{{ '<|im_start|>assistant\\n' }}{% endif %}"

        val out = ChatTemplateRenderer.render(
            tpl,
            listOf(msg("system", "You are helpful"), msg("user", "Hi")),
        )!!
        assertTrue(out.contains("<|im_start|>system\nYou are helpful<|im_end|>"))
        assertTrue(out.contains("<|im_start|>user\nHi<|im_end|>"))
        assertTrue(out.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun `renders Llama-3 template with generation prompt`() {
        val tpl = "{{ bos_token }}{% for message in messages %}" +
            "{% if message.role == 'user' %}{{ '<|start_header_id|>user<|end_header_id|>\\n\\n' + message.content + '<|eot_id|>' }}" +
            "{% elif message.role == 'assistant' %}{{ '<|start_header_id|>assistant<|end_header_id|>\\n\\n' + message.content + '<|eot_id|>' }}" +
            "{% endif %}{% endfor %}" +
            "{% if add_generation_prompt %}{{ '<|start_header_id|>assistant<|end_header_id|>\\n\\n' }}{% endif %}"

        val out = ChatTemplateRenderer.render(
            tpl,
            listOf(msg("user", "Hello")),
            bosToken = "<|begin_of_text|>",
        )!!
        assertTrue(out.startsWith("<|begin_of_text|>"))
        assertTrue(out.contains("<|start_header_id|>user<|end_header_id|>\n\nHello<|eot_id|>"))
        assertTrue(out.endsWith("<|start_header_id|>assistant<|end_header_id|>\n\n"))
    }

    @Test
    fun `renders Mistral template`() {
        val tpl = "{% for message in messages %}{% if message.role == 'user' %}" +
            "{{ '[INST] ' + message.content + ' [/INST]' }}" +
            "{% elif message.role == 'assistant' %}{{ message.content + ' ' }}" +
            "{% endif %}{% endfor %}"

        val out = ChatTemplateRenderer.render(
            tpl,
            listOf(msg("user", "Explain X"), msg("assistant", "Sure")),
            addGenerationPrompt = false,
        )!!
        assertEquals("[INST] Explain X [/INST]Sure ", out)
    }

    @Test
    fun `renders Qwen2_5 template`() {
        val tpl = "{% for message in messages %}{% if message.role == 'system' %}" +
            "{{ '<|im_start|>system\\n' + message.content + '<|im_end|>\\n' }}" +
            "{% elif message.role == 'user' %}{{ '<|im_start|>user\\n' + message.content + '<|im_end|>\\n' }}" +
            "{% elif message.role == 'assistant' %}{{ '<|im_start|>assistant\\n' + message.content + '<|im_end|>\\n' }}" +
            "{% endif %}{% endfor %}{% if add_generation_prompt %}{{ '<|im_start|>assistant\\n' }}{% endif %}"

        val out = ChatTemplateRenderer.render(
            tpl,
            listOf(msg("system", "You are Qwen"), msg("user", "你好")),
        )!!
        assertTrue(out.startsWith("<|im_start|>system\nYou are Qwen<|im_end|>"))
        assertTrue(out.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun `renders Gemma template with whitespace control`() {
        val tpl = "{{ bos_token }}{% for message in messages %}" +
            "{% if message.role == 'user' %}{{ '<start_of_turn>user\\n' + message.content + '<end_of_turn>\\n' }}" +
            "{% elif message.role == 'assistant' %}{{ '<start_of_turn>model\\n' + message.content + '<end_of_turn>\\n' }}" +
            "{% endif %}{% endfor %}{% if add_generation_prompt %}{{ '<start_of_turn>model\\n' }}{% endif %}"

        val out = ChatTemplateRenderer.render(
            tpl,
            listOf(msg("user", "hi")),
            bosToken = "<bos>",
        )!!
        assertTrue(out.startsWith("<bos><start_of_turn>user\nhi<end_of_turn>\n"))
        assertTrue(out.endsWith("<start_of_turn>model\n"))
    }

    @Test
    fun `strips whitespace around lstrip and rstrip control`() {
        val tpl = "  {%- if true -%}  hello  {%- endif -%}  world"
        val out = ChatTemplateRenderer.render(tpl, emptyList())!!
        // Each -%} eats the whitespace that follows it and {%- eats what precedes it,
        // so both the outer padding and the body padding collapse — matches Jinja2.
        assertEquals("helloworld", out)
    }

    @Test
    fun `supports set and loop variables`() {
        val tpl = "{% set n = messages | length %}[{{ n }}]{% for m in messages %}" +
            "{{ loop.index0 }}/{{ loop.index }}/{{ loop.first }}/{{ loop.last }}:{{ m.role }} " +
            "{% endfor %}"
        val out = ChatTemplateRenderer.render(
            tpl,
            listOf(msg("user", "a"), msg("assistant", "b"), msg("user", "c")),
            addGenerationPrompt = false,
        )!!
        assertEquals("[3]0/1/true/false:user 1/2/false/false:assistant 2/3/false/true:user ", out)
    }

    @Test
    fun `trim_messages drops trailing empty assistant turn`() {
        val tpl = "{{ messages | trim_messages | length }}"
        val out = ChatTemplateRenderer.render(
            tpl,
            listOf(msg("user", "a"), msg("assistant", "b")),
        )!!
        // addGenerationPrompt appends an empty assistant turn; trim_messages removes it.
        assertEquals("2", out)
    }

    @Test
    fun `filters replace join lower default tojson`() {
        val tpl = "{{ '  AbC  ' | trim | lower }}{{ [1, 2] | join(',') }}" +
            "{{ none | default('nil') }}{{ '' | default('dflt', true) }}" +
            "{{ 'x' | tojson }}"
        val out = ChatTemplateRenderer.render(tpl, emptyList())!!
        assertEquals("abc1,2nildflt\"x\"", out)
    }

    @Test
    fun `no generation prompt when add_generation_prompt is false`() {
        val tpl = "{% for message in messages %}{{ message.content }}{% endfor %}" +
            "{% if add_generation_prompt %}[GEN]{% endif %}"
        val out = ChatTemplateRenderer.render(tpl, listOf(msg("user", "hi")), addGenerationPrompt = false)!!
        assertEquals("hi", out)
    }

    @Test
    fun `unsupported statement returns null`() {
        val tpl = "{% include 'other' %}{{ x }}"
        assertNull(ChatTemplateRenderer.render(tpl, emptyList()))
    }

    @Test
    fun `malformed template returns null`() {
        assertNull(ChatTemplateRenderer.render("{% for x in", emptyList()))
        assertNull(ChatTemplateRenderer.render("{{ never closed", emptyList()))
    }

    @Test
    fun `tool_calls truthiness drives tool branch`() {
        val tpl = "{% for message in messages %}{% if message.tool_calls %}" +
            "[TOOL]{% else %}{{ message.content }}{% endif %}{% endfor %}"
        val withTool = ChatTemplateRenderer.Message(
            role = "assistant", content = null,
            toolCalls = listOf(mapOf("function" to mapOf("name" to "search"))),
        )
        val out = ChatTemplateRenderer.render(
            tpl,
            listOf(msg("user", "q"), withTool, msg("tool", "result")),
        )!!
        assertEquals("q[TOOL]result", out)
    }

    @Test
    fun `empty message list with generation prompt still works`() {
        val tpl = "{% if add_generation_prompt %}{{ '<|im_start|>assistant\\n' }}{% endif %}"
        assertEquals("<|im_start|>assistant\n", ChatTemplateRenderer.render(tpl, emptyList())!!)
    }
}
