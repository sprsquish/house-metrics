package housemetrics

import (
	"io"

	"github.com/rs/zerolog"
)

type LevelWriter struct {
	Std io.Writer
	Err io.Writer
}

func (lw LevelWriter) Write(p []byte) (n int, err error) {
	return lw.Std.Write(p)
}

// WriteLevel fulfills the interface for zerolog.LevelWriter
func (lw LevelWriter) WriteLevel(level zerolog.Level, bytes []byte) (n int, err error) {
	writer := lw.Std
	if level > zerolog.InfoLevel {
		writer = lw.Err
	}
	return writer.Write(bytes)
}
