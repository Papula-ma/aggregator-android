import codecs
import dateutil.parser
import jinja2
import os
import markdown
import shutil

CONTENTS = {}
JINJA2 = jinja2.Environment(loader=jinja2.FileSystemLoader('templates'))
TEMPLATES = {
    'howto': JINJA2.get_template('howto.jinja2'),
    'news': JINJA2.get_template('news.jinja2'),
}


def process_content():
    news = []

    os.chdir('content')
    for root, dirs, files in os.walk('./'):
        dirs.sort(reverse=True)

        if root.startswith('./'):
            root = root[2:]

        try:
            os.mkdir(os.path.join('../gh-pages', root))
        except OSError:
            pass

        for file_name in sorted(files):
            file_path = os.path.join(root, file_name)

            if file_name.endswith('.content'):
                content = load_content(file_path)

                content_type = content['type']
                if content_type == 'news':
                    news.append(content)
                else:
                    generate_html(content)

            else:
                print(f"""Copying {file_path}""")
                shutil.copy(file_path, os.path.join('../gh-pages', file_path))

    generate_rss(news)


def load_content(file_path):
    if file_path not in CONTENTS:
        print(f"""Loading {file_path}""")

        with codecs.open(file_path, mode='r', encoding='utf-8') as reader:
            content = CONTENTS[file_path] = {
                'file': file_path
            }

            for parameter in reader:
                parameter = parameter.strip()
                if not parameter:
                    break
                name, value = parameter.split(':', 1)
                name = name.strip()
                value = value.strip()

                if name == 'date':
                    content[name] = dateutil.parser.parse(value)
                else:
                    content[name] = value

            content_markdown = reader.read()
            if content_markdown:
                content['content'] = jinja2.Markup(markdown.markdown(content_markdown))

    return CONTENTS[file_path]


def generate_html(content):
    content_type = content['type']
    content_file = content['file']

    print(f"""Generating {content_type} from {content_file}""")

    template = TEMPLATES[content_type]

    with codecs.open(os.path.join('../gh-pages', content_file[:-7] + 'html'), mode='w', encoding='utf-8') as writer:
        writer.write(template.render(**content))


def generate_rss(news):
    if not news:
        return

    print(f"""Generating RSS""")

    template = TEMPLATES['news']

    for item in news:
        if 'page' in item:
            page = CONTENTS[item['page']].copy()
            page.update(item)
            item.update(page)

    with codecs.open(os.path.join('../gh-pages', 'news.rss'), mode='w', encoding='utf-8') as writer:
        writer.write(template.render({'news': news}))


if __name__ == '__main__':
    print(f"""Deleting generated files""")
    for root, dirs, files in reversed([x for x in os.walk('gh-pages')]):
        for file_name in files:
            if root == 'gh-pages' and file_name.startswith('.'):
                continue
            print(f"""Deleting file: {os.path.join(root, file_name)}""")
            os.remove(os.path.join(root, file_name))
        if root != 'gh-pages':
            print(f"""Deleting directory: {root}""")
            os.rmdir(root)

    process_content()

