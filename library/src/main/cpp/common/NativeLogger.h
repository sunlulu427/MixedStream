#ifndef ASTRASTREAM_NATIVELOGGER_H
#define ASTRASTREAM_NATIVELOGGER_H

#include <string>

namespace astra {

void initLogger(const std::string& path);
void logLine(int level, const std::string& tag, const std::string& message);

}

#endif  // ASTRASTREAM_NATIVELOGGER_H
