import logging

from .environment import LOG_LEVEL


class BlenderFormatter(logging.Formatter):
    """Format runtime messages like Blender console log entries without terminal control codes."""

    def __init__(self):
        super().__init__(
            "%(blender_time)s  blender.pycharm  | %(levelname)s: %(message)s (%(filename)s:%(lineno)d)"
        )

    def format(self, record):
        elapsed_milliseconds = max(0, round(record.relativeCreated))
        minutes, milliseconds_in_minute = divmod(elapsed_milliseconds, 60_000)
        seconds, milliseconds = divmod(milliseconds_in_minute, 1_000)
        record.blender_time = f"{minutes:02d}:{seconds:02d}.{milliseconds:03d}"
        return super().format(record)


def get_logger(name: str = "blender_vs"):
    logging.getLogger().setLevel(LOG_LEVEL)

    log = logging.getLogger(name)
    if log.handlers:
        # log is already configured
        return log
    log.propagate = False
    log.setLevel(LOG_LEVEL)

    # create console handler with a higher log level
    ch = logging.StreamHandler()
    ch.setLevel(logging.DEBUG)

    ch.setFormatter(BlenderFormatter())

    log.addHandler(ch)

    return log
