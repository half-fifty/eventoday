import { useCallback, useEffect, useRef, useState } from "react";
import { EditorContent, useEditor, useEditorState } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import { TextAlign } from "@tiptap/extension-text-align";
import { Image } from "@tiptap/extension-image";
import { TableKit } from "@tiptap/extension-table";
import { FontSize, TextStyle } from "@tiptap/extension-text-style";
import Icon from "./Icon.jsx";
import { ApiError } from "../api/apiClient.js";
import { fileDownloadUrl, uploadFile } from "../api/fileApi.js";

// 리치 텍스트 에디터 (WYSIWYG)
//
// 결과물은 HTML 문자열이며 onChange로 전달한다.
// 저장 시 서버(HtmlSanitizer)가 다시 정제하므로, 여기서 만드는 태그·속성은
// 서버 허용 목록 안에 있는 것만 사용한다. 목록 밖의 서식은 저장 후 사라진다.
//
// 본문 영역에 .rich-text 클래스를 씌워 조회 화면(RichTextViewer)과 같은 모양으로 보이게 한다.

const HEADING_LEVELS = [1, 2, 3];

// 글자 크기 — 서버가 style에서 font-size만 허용하므로 px 값으로 지정한다
const FONT_SIZES = [
  { label: "작게", value: "13px" },
  { label: "보통", value: "" },
  { label: "크게", value: "18px" },
  { label: "아주 크게", value: "22px" },
];

const EXTENSIONS = [
  StarterKit.configure({
    heading: { levels: HEADING_LEVELS },
    link: {
      openOnClick: false,
      autolink: true,
      HTMLAttributes: { target: "_blank", rel: "noopener noreferrer nofollow" },
    },
  }),
  TextStyle,
  FontSize,
  TextAlign.configure({ types: ["heading", "paragraph"] }),
  // 이미지를 문단 안에 두어 문단 정렬(가운데·오른쪽)이 이미지에도 적용되게 한다.
  // 기본값(블록 노드)으로 두면 이미지가 문단 밖에 놓여 정렬 버튼의 영향을 받지 않는다.
  Image.configure({ inline: true }),
  TableKit.configure({ table: { resizable: true } }),
];

/**
 * 링크 주소 정규화.
 * 스킴이 없으면 https를 붙이고, http·https·mailto·루트 상대 경로 외의 스킴은 거부한다
 * (javascript: 같은 주소가 저장되는 것을 입력 단계에서 막는다)
 */
const normalizeUrl = (rawUrl) => {
  if (/^(?:https?:\/\/|mailto:|\/(?!\/))/i.test(rawUrl)) return rawUrl;
  if (/^[a-z][a-z0-9+.-]*:/i.test(rawUrl)) return null;
  return `https://${rawUrl}`;
};

// 툴바 버튼 — 활성 상태를 기존 primary 색으로 표시한다
function ToolbarButton({ onClick, active = false, disabled = false, label, icon, children }) {
  return (
    <button
      type="button"
      onMouseDown={(event) => event.preventDefault()} // 버튼 클릭으로 에디터 선택이 풀리지 않도록
      onClick={onClick}
      disabled={disabled}
      title={label}
      aria-label={label}
      aria-pressed={active}
      className={`h-[30px] min-w-[30px] px-xs rounded-lg flex items-center justify-center transition-colors disabled:opacity-40 ${
        active ? "bg-primary-focus/10 text-primary-focus" : "text-ink-muted hover:bg-surface-container"
      }`}
    >
      {icon ? <Icon name={icon} className="text-[17px]" /> : children}
    </button>
  );
}

function ToolbarDivider() {
  return <span className="w-px h-[18px] bg-hairline mx-xxs" aria-hidden="true" />;
}

export default function RichTextEditor({
  value = "",
  onChange,
  disabled = false,
  minHeight = 260,
}) {
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState("");
  const fileInputRef = useRef(null);

  // useEditor는 생성 시점의 옵션을 붙잡아 두므로, 최신 onChange를 ref로 참조한다
  const onChangeRef = useRef(onChange);
  useEffect(() => { onChangeRef.current = onChange; }, [onChange]);

  const editor = useEditor({
    extensions: EXTENSIONS,
    content: value || "",
    editable: !disabled,
    editorProps: {
      attributes: {
        class: "rich-text focus:outline-none px-md py-sm",
        style: `min-height:${minHeight}px`,
      },
    },
    onUpdate: ({ editor: current }) => {
      // 내용을 지웠을 때 <p></p>가 남지 않도록 빈 문서는 빈 문자열로 전달한다
      onChangeRef.current?.(current.isEmpty ? "" : current.getHTML());
    },
  });

  // v3부터 트랜잭션마다 다시 렌더링하지 않으므로, 툴바 활성 상태는 useEditorState로 구독한다
  const toolbar = useEditorState({
    editor,
    selector: ({ editor: current }) => {
      if (!current) return null;
      return {
        bold: current.isActive("bold"),
        italic: current.isActive("italic"),
        underline: current.isActive("underline"),
        strike: current.isActive("strike"),
        bulletList: current.isActive("bulletList"),
        orderedList: current.isActive("orderedList"),
        blockquote: current.isActive("blockquote"),
        link: current.isActive("link"),
        alignLeft: current.isActive({ textAlign: "left" }),
        alignCenter: current.isActive({ textAlign: "center" }),
        alignRight: current.isActive({ textAlign: "right" }),
        headingLevel: HEADING_LEVELS.find((level) => current.isActive("heading", { level })) || "",
        fontSize: current.getAttributes("textStyle").fontSize || "",
        canUndo: current.can().undo(),
        canRedo: current.can().redo(),
      };
    },
  });

  // 수정 화면에서 기존 본문을 불러올 때처럼 외부 값이 바뀌면 에디터 내용을 맞춘다.
  // 입력 중에는 value와 getHTML()이 같아 setContent가 실행되지 않는다(커서 튐 방지).
  useEffect(() => {
    if (!editor) return;
    const nextValue = value || "";
    if (editor.isEmpty && !nextValue) return;
    if (nextValue === editor.getHTML()) return;
    editor.commands.setContent(nextValue, { emitUpdate: false });
  }, [editor, value]);

  useEffect(() => {
    editor?.setEditable(!disabled);
  }, [editor, disabled]);

  const handleLink = useCallback(() => {
    if (!editor) return;
    const previousUrl = editor.getAttributes("link").href || "";
    const input = window.prompt("링크 주소를 입력하세요. 비워두면 링크가 해제됩니다.", previousUrl);
    if (input === null) return; // 취소

    if (!input.trim()) {
      editor.chain().focus().extendMarkRange("link").unsetLink().run();
      return;
    }
    const url = normalizeUrl(input.trim());
    if (!url) {
      setUploadError("사용할 수 없는 주소입니다.");
      return;
    }
    setUploadError("");
    editor.chain().focus().extendMarkRange("link").setLink({ href: url }).run();
  }, [editor]);

  // 이미지는 기존 공통 업로드 API를 그대로 쓰고, 다운로드 경로를 src로 넣는다.
  // 다운로드 API가 매번 Presigned URL로 리다이렉트하므로 URL 만료를 신경 쓰지 않아도 된다.
  const handleImageSelected = useCallback(async (event) => {
    const file = event.target.files?.[0];
    event.target.value = ""; // 같은 파일을 다시 고를 수 있도록 초기화
    if (!file || !editor) return;

    setUploading(true);
    setUploadError("");
    try {
      const uploaded = await uploadFile(file, "PUBLIC");
      editor.chain().focus().setImage({ src: fileDownloadUrl(uploaded.fileId), alt: file.name }).run();
    } catch (err) {
      setUploadError(
        err instanceof ApiError ? `${err.code}: ${err.message}` : (err?.message || "이미지 업로드에 실패했습니다."),
      );
    } finally {
      setUploading(false);
    }
  }, [editor]);

  if (!editor || !toolbar) return null;

  const chain = () => editor.chain().focus();

  return (
    <div className="border border-hairline rounded-xl bg-white overflow-hidden focus-within:border-primary-focus transition-colors">
      {/* 툴바 — 모바일에서는 줄바꿈된다 */}
      <div className="flex flex-wrap items-center gap-xxs px-sm py-xs border-b border-hairline bg-surface-pearl">
        <ToolbarButton label="실행 취소" icon="undo" disabled={disabled || !toolbar.canUndo}
          onClick={() => chain().undo().run()} />
        <ToolbarButton label="다시 실행" icon="redo" disabled={disabled || !toolbar.canRedo}
          onClick={() => chain().redo().run()} />

        <ToolbarDivider />

        {/* 제목 단계 */}
        <select
          value={toolbar.headingLevel}
          disabled={disabled}
          aria-label="글 단계"
          onChange={(event) => {
            const level = Number(event.target.value);
            if (level) chain().setHeading({ level }).run();
            else chain().setParagraph().run();
          }}
          className="h-[30px] px-xs rounded-lg border border-hairline bg-white text-caption text-on-surface disabled:opacity-40"
        >
          <option value="">본문</option>
          {HEADING_LEVELS.map((level) => (
            <option key={level} value={level}>제목 {level}</option>
          ))}
        </select>

        {/* 글자 크기 */}
        <select
          value={toolbar.fontSize}
          disabled={disabled}
          aria-label="글자 크기"
          onChange={(event) => {
            const size = event.target.value;
            if (size) chain().setFontSize(size).run();
            else chain().unsetFontSize().run();
          }}
          className="h-[30px] px-xs rounded-lg border border-hairline bg-white text-caption text-on-surface disabled:opacity-40"
        >
          {FONT_SIZES.map((size) => (
            <option key={size.label} value={size.value}>{size.label}</option>
          ))}
        </select>

        <ToolbarDivider />

        <ToolbarButton label="굵게" icon="format_bold" active={toolbar.bold} disabled={disabled}
          onClick={() => chain().toggleBold().run()} />
        <ToolbarButton label="기울임" icon="format_italic" active={toolbar.italic} disabled={disabled}
          onClick={() => chain().toggleItalic().run()} />
        <ToolbarButton label="밑줄" icon="format_underlined" active={toolbar.underline} disabled={disabled}
          onClick={() => chain().toggleUnderline().run()} />
        <ToolbarButton label="취소선" icon="format_strikethrough" active={toolbar.strike} disabled={disabled}
          onClick={() => chain().toggleStrike().run()} />

        <ToolbarDivider />

        <ToolbarButton label="왼쪽 정렬" icon="format_align_left" active={toolbar.alignLeft} disabled={disabled}
          onClick={() => chain().setTextAlign("left").run()} />
        <ToolbarButton label="가운데 정렬" icon="format_align_center" active={toolbar.alignCenter} disabled={disabled}
          onClick={() => chain().setTextAlign("center").run()} />
        <ToolbarButton label="오른쪽 정렬" icon="format_align_right" active={toolbar.alignRight} disabled={disabled}
          onClick={() => chain().setTextAlign("right").run()} />

        <ToolbarDivider />

        <ToolbarButton label="글머리 기호 목록" icon="format_list_bulleted" active={toolbar.bulletList} disabled={disabled}
          onClick={() => chain().toggleBulletList().run()} />
        <ToolbarButton label="번호 매기기 목록" icon="format_list_numbered" active={toolbar.orderedList} disabled={disabled}
          onClick={() => chain().toggleOrderedList().run()} />
        <ToolbarButton label="인용문" icon="format_quote" active={toolbar.blockquote} disabled={disabled}
          onClick={() => chain().toggleBlockquote().run()} />

        <ToolbarDivider />

        <ToolbarButton label="링크" icon="link" active={toolbar.link} disabled={disabled} onClick={handleLink} />
        <ToolbarButton label="이미지" icon="image" disabled={disabled || uploading}
          onClick={() => fileInputRef.current?.click()} />
        <ToolbarButton label="표 삽입" icon="table" disabled={disabled}
          onClick={() => chain().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()} />
      </div>

      <input
        ref={fileInputRef}
        type="file"
        accept="image/png,image/jpeg,image/gif,image/webp"
        className="hidden"
        onChange={handleImageSelected}
      />

      {uploading && (
        <p className="px-md pt-xs text-caption text-ink-muted">이미지를 업로드하는 중...</p>
      )}
      {uploadError && (
        <p className="px-md pt-xs text-caption text-error">{uploadError}</p>
      )}

      <EditorContent editor={editor} />
    </div>
  );
}
