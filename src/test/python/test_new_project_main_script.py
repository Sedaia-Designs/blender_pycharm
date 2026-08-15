import unittest
from pathlib import Path

TEMPLATE_PATH = (
    Path(__file__).parents[2]
    / "main/resources/fileTemplates/internal/NewProjectMainScript.ft"
)


class NewProjectMainScriptTest(unittest.TestCase):
    def test_minimal_generated_script_compiles(self):
        source = self.render_template(new_project=True, example_code=False)

        compile(source, str(TEMPLATE_PATH), "exec")

    def test_example_generated_script_compiles(self):
        source = self.render_template(new_project=True, example_code=True)

        compile(source, str(TEMPLATE_PATH), "exec")

    @staticmethod
    def render_template(new_project: bool, example_code: bool) -> str:
        conditions = {
            "$newProject": new_project,
            "$exampleCode": example_code,
        }
        active_sections = [True]
        rendered_lines = []

        for line in TEMPLATE_PATH.read_text(encoding="utf-8").splitlines():
            if line.startswith("#if ("):
                variable, expected = line.removeprefix("#if (").removesuffix(")").split(" == ")
                condition_matches = conditions[variable] == (expected.strip('"') == "true")
                active_sections.append(active_sections[-1] and condition_matches)
                continue
            if line == "#else":
                parent_active = active_sections[-2]
                active_sections[-1] = parent_active and not active_sections[-1]
                continue
            if line == "#end":
                active_sections.pop()
                continue
            if active_sections[-1]:
                rendered_lines.append(line)

        source = "\n".join(rendered_lines)
        substitutions = {
            "${name}": "Example Add-on",
            "${author}": "Test Author",
            "${version}": "(1, 0, 0)",
            "${blenderVersion}": "(4, 2, 0)",
            "${description}": "Generated test project",
        }
        for placeholder, value in substitutions.items():
            source = source.replace(placeholder, value)
        return source


if __name__ == "__main__":
    unittest.main()
