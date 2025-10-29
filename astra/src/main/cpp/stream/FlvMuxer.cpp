#include "FlvMuxer.h"

#include <algorithm>
#include <array>
#include <cmath>
#include <cstddef>
#include <cstring>

namespace astra {

namespace {

constexpr uint8_t kFlvSoundFormatAac = 10;
constexpr uint8_t kFlvSoundRate44k = 3;
constexpr uint8_t kFlvSoundSize16Bit = 1;
constexpr uint8_t kFlvSoundTypeStereo = 1;

constexpr uint8_t kFlvVideoFrameKey = 1;
constexpr uint8_t kFlvVideoFrameInter = 2;

constexpr uint8_t kFlvAvcSequenceHeader = 0;
constexpr uint8_t kFlvAvcNalu = 1;

constexpr uint8_t kAudNalTypeH264 = 9;
constexpr uint8_t kSpsNalTypeH264 = 7;
constexpr uint8_t kPpsNalTypeH264 = 8;
constexpr uint8_t kIdrNalTypeH264 = 5;

constexpr uint8_t kAudNalTypeH265 = 35;
constexpr uint8_t kVpsNalTypeH265 = 32;
constexpr uint8_t kSpsNalTypeH265 = 33;
constexpr uint8_t kPpsNalTypeH265 = 34;
constexpr uint8_t kIdrWRadlTypeH265 = 19;
constexpr uint8_t kIdrNLpTypeH265 = 20;
constexpr uint8_t kCraTypeH265 = 21;

struct StartCode {
    size_t offset = 0;
    size_t length = 0;
    bool found = false;
};

StartCode FindStartCode(const uint8_t* data, size_t start, size_t size) {
    StartCode result{};
    if (data == nullptr || start >= size) {
        return result;
    }

    for (size_t i = start; i + 3 < size; ++i) {
        if (data[i] == 0x00 && data[i + 1] == 0x00) {
            if (data[i + 2] == 0x01) {
                result.offset = i;
                result.length = 3;
                result.found = true;
                return result;
            }
            if (i + 4 < size && data[i + 2] == 0x00 && data[i + 3] == 0x01) {
                result.offset = i;
                result.length = 4;
                result.found = true;
                return result;
            }
        }
    }
    return result;
}

std::vector<std::vector<uint8_t>> SplitAnnexbNalUnits(const uint8_t* data,
                                                       size_t size) {
    std::vector<std::vector<uint8_t>> nalUnits;
    if (data == nullptr || size == 0) {
        return nalUnits;
    }

    size_t position = 0;
    StartCode start = FindStartCode(data, position, size);
    if (!start.found) {
        return nalUnits;
    }

    position = start.offset + start.length;
    while (true) {
        StartCode next = FindStartCode(data, position, size);
        size_t nalEnd = next.found ? next.offset : size;
        if (nalEnd > position) {
            nalUnits.emplace_back(data + position, data + nalEnd);
        }
        if (!next.found) {
            break;
        }
        position = next.offset + next.length;
    }
    return nalUnits;
}

std::vector<std::vector<uint8_t>> SplitLengthPrefixedNalUnits(const uint8_t* data,
                                                              size_t size) {
    std::vector<std::vector<uint8_t>> nalUnits;
    if (data == nullptr || size < 4) {
        return nalUnits;
    }
    size_t position = 0;
    while (position + 4 <= size) {
        uint32_t nalSize = (static_cast<uint32_t>(data[position]) << 24) |
                           (static_cast<uint32_t>(data[position + 1]) << 16) |
                           (static_cast<uint32_t>(data[position + 2]) << 8) |
                           (static_cast<uint32_t>(data[position + 3]));
        position += 4;
        if (nalSize == 0 || position + nalSize > size) {
            break;
        }
        nalUnits.emplace_back(data + position, data + position + nalSize);
        position += nalSize;
    }
    return nalUnits;
}

uint32_t AudioSampleRateToIndex(uint32_t sampleRate) {
    switch (sampleRate) {
        case 96000: return 0;
        case 88200: return 1;
        case 64000: return 2;
        case 48000: return 3;
        case 44100: return 4;
        case 32000: return 5;
        case 24000: return 6;
        case 22050: return 7;
        case 16000: return 8;
        case 12000: return 9;
        case 11025: return 10;
        case 8000: return 11;
        case 7350: return 12;
        default: return 4;  // default to 44.1kHz
    }
}

// Bit reader used for HEVC profile parsing
class BitReader {
public:
    explicit BitReader(const std::vector<uint8_t>& data) : data_(data) {}

    uint32_t readBits(uint32_t count) {
        uint32_t value = 0;
        for (uint32_t i = 0; i < count; ++i) {
            value <<= 1U;
            if (byteOffset_ >= data_.size()) {
                continue;
            }
            uint8_t current = data_[byteOffset_];
            uint8_t bit = (current >> (7U - bitOffset_)) & 0x01U;
            value |= bit;
            ++bitOffset_;
            if (bitOffset_ == 8U) {
                bitOffset_ = 0U;
                ++byteOffset_;
            }
        }
        return value;
    }

    uint32_t readBit() { return readBits(1U); }

    uint32_t readUE() {
        uint32_t leadingZeroBits = 0;
        while (readBit() == 0U && leadingZeroBits < 32U) {
            ++leadingZeroBits;
        }
        if (leadingZeroBits == 32U) {
            return 0U;
        }
        if (leadingZeroBits == 0U) {
            return 1U;
        }
        uint32_t suffix = readBits(leadingZeroBits);
        return ((1U << leadingZeroBits) - 1U) + suffix;
    }

private:
    const std::vector<uint8_t>& data_;
    size_t byteOffset_ = 0;
    uint32_t bitOffset_ = 0;
};

std::vector<uint8_t> ToRbsp(const std::vector<uint8_t>& nal) {
    std::vector<uint8_t> rbsp;
    if (nal.size() <= 2) {
        return rbsp;
    }
    rbsp.reserve(nal.size());
    uint32_t zeroCount = 0;
    for (size_t i = 2; i < nal.size(); ++i) {
        uint8_t byte = nal[i];
        if (zeroCount >= 2 && byte == 0x03) {
            zeroCount = 0;
            continue;
        }
        rbsp.push_back(byte);
        zeroCount = (byte == 0) ? zeroCount + 1 : 0;
    }
    return rbsp;
}

}  // namespace

void FlvMuxer::reset() {
    metadataSent_ = false;
    videoSequenceSent_ = false;
    audioSequenceSent_ = false;
    sps_.clear();
    pps_.clear();
    vps_.clear();
}

void FlvMuxer::setVideoConfig(const VideoConfig& config) {
    videoConfig_ = config;
    metadataSent_ = false;
    videoSequenceSent_ = false;
}

void FlvMuxer::setAudioConfig(const AudioConfig& config) {
    audioConfig_ = config;
    metadataSent_ = false;
    audioSequenceSent_ = false;
}

bool FlvMuxer::videoSequenceReady() const {
    if (videoConfig_.codec == VideoCodecId::kH264) {
        return !sps_.empty() && !pps_.empty();
    }
    return !vps_.empty() && !sps_.empty() && !pps_.empty();
}

bool FlvMuxer::audioSequenceReady() const {
    return !audioConfig_.asc.empty();
}

std::optional<std::vector<uint8_t>> FlvMuxer::buildMetadataTag() const {
    if (videoConfig_.width == 0 || videoConfig_.height == 0 || videoConfig_.fps == 0) {
        return std::nullopt;
    }

    std::vector<uint8_t> payload;

    auto writeString = [&payload](const char* value) {
        size_t len = std::strlen(value);
        payload.push_back(0x02);  // string
        payload.push_back(static_cast<uint8_t>((len >> 8) & 0xFF));
        payload.push_back(static_cast<uint8_t>(len & 0xFF));
        payload.insert(payload.end(), value, value + len);
    };

    auto writeNumberProperty = [&payload](const char* key, double number) {
        size_t len = std::strlen(key);
        payload.push_back(static_cast<uint8_t>((len >> 8) & 0xFF));
        payload.push_back(static_cast<uint8_t>(len & 0xFF));
        payload.insert(payload.end(), key, key + len);
        payload.push_back(0x00);  // number marker

        union {
            double value;
            uint8_t bytes[8];
        } converter{};
        converter.value = number;
        for (int i = 7; i >= 0; --i) {
            payload.push_back(converter.bytes[i]);
        }
    };

    auto writeBooleanProperty = [&payload](const char* key, bool flag) {
        size_t len = std::strlen(key);
        payload.push_back(static_cast<uint8_t>((len >> 8) & 0xFF));
        payload.push_back(static_cast<uint8_t>(len & 0xFF));
        payload.insert(payload.end(), key, key + len);
        payload.push_back(0x01);  // boolean marker
        payload.push_back(flag ? 0x01 : 0x00);
    };

    writeString("onMetaData");
    payload.push_back(0x08);  // ECMA array
    payload.push_back(0x00);
    payload.push_back(0x00);
    payload.push_back(0x00);
    payload.push_back(0x07);  // number of elements

    writeNumberProperty("width", static_cast<double>(videoConfig_.width));
    writeNumberProperty("height", static_cast<double>(videoConfig_.height));
    writeNumberProperty("framerate", static_cast<double>(videoConfig_.fps));
    writeNumberProperty("videocodecid", videoConfig_.codec == VideoCodecId::kH264 ? 7.0 : 12.0);
    writeNumberProperty("audiosamplerate", static_cast<double>(audioConfig_.sampleRate));
    writeNumberProperty("audiosamplesize", static_cast<double>(audioConfig_.sampleSizeBits));
    writeBooleanProperty("stereo", audioConfig_.channels > 1);
    writeNumberProperty("audiocodecid", 10.0);  // AAC

    payload.push_back(0x00);
    payload.push_back(0x00);
    payload.push_back(0x09);  // end of object

    return payload;
}

std::optional<std::vector<uint8_t>> FlvMuxer::buildVideoSequenceHeader() {
    if (!videoSequenceReady()) {
        return std::nullopt;
    }

    std::vector<uint8_t> payload;
    payload.reserve(5 + sps_.size() + pps_.size() + vps_.size() + 64);

    uint8_t header = buildVideoHeader(videoConfig_.codec, true, true);
    payload.push_back(header);
    payload.push_back(kFlvAvcSequenceHeader);
    payload.push_back(0x00);
    payload.push_back(0x00);
    payload.push_back(0x00);

    if (videoConfig_.codec == VideoCodecId::kH264) {
        auto config = buildAvcDecoderConfigurationRecord();
        payload.insert(payload.end(), config.begin(), config.end());
    } else {
        auto config = buildHevcDecoderConfigurationRecord();
        payload.insert(payload.end(), config.begin(), config.end());
    }

    videoSequenceSent_ = true;
    return payload;
}

std::optional<std::vector<uint8_t>> FlvMuxer::buildAudioSequenceHeader() const {
    if (!audioSequenceReady()) {
        return std::nullopt;
    }

    std::vector<uint8_t> payload;
    payload.reserve(2 + audioConfig_.asc.size());

    const auto header = buildAudioHeader(audioConfig_, true);
    payload.push_back(header[0]);
    payload.push_back(header[1]);
    payload.insert(payload.end(), audioConfig_.asc.begin(), audioConfig_.asc.end());
    return payload;
}

ParsedVideoFrame FlvMuxer::parseVideoFrame(const uint8_t* data, size_t size) {
    ParsedVideoFrame frame;
    if (data == nullptr || size == 0) {
        return frame;
    }

    std::vector<std::vector<uint8_t>> nalUnits = SplitAnnexbNalUnits(data, size);
    if (nalUnits.empty()) {
        nalUnits = SplitLengthPrefixedNalUnits(data, size);
    }

    bool keyFrame = false;
    std::vector<uint8_t> payload;

    for (const auto& nal : nalUnits) {
        if (nal.empty()) {
            continue;
        }

        uint8_t nalType = 0;
        if (videoConfig_.codec == VideoCodecId::kH264) {
            nalType = nal[0] & 0x1F;
            if (nalType == kAudNalTypeH264) {
                continue;
            }
            if (nalType == kSpsNalTypeH264) {
                sps_.assign(nal.begin(), nal.end());
                continue;
            }
            if (nalType == kPpsNalTypeH264) {
                pps_.assign(nal.begin(), nal.end());
                continue;
            }
            if (nalType == kIdrNalTypeH264) {
                keyFrame = true;
            }
        } else {
            nalType = static_cast<uint8_t>((nal[0] >> 1) & 0x3F);
            if (nalType == kAudNalTypeH265) {
                continue;
            }
            if (nalType == kVpsNalTypeH265) {
                vps_.assign(nal.begin(), nal.end());
                continue;
            }
            if (nalType == kSpsNalTypeH265) {
                sps_.assign(nal.begin(), nal.end());
                continue;
            }
            if (nalType == kPpsNalTypeH265) {
                pps_.assign(nal.begin(), nal.end());
                continue;
            }
            if (nalType == kIdrWRadlTypeH265 || nalType == kIdrNLpTypeH265 || nalType == kCraTypeH265) {
                keyFrame = true;
            }
        }

        uint32_t nalSize = static_cast<uint32_t>(nal.size());
        payload.push_back(static_cast<uint8_t>((nalSize >> 24) & 0xFF));
        payload.push_back(static_cast<uint8_t>((nalSize >> 16) & 0xFF));
        payload.push_back(static_cast<uint8_t>((nalSize >> 8) & 0xFF));
        payload.push_back(static_cast<uint8_t>(nalSize & 0xFF));
        payload.insert(payload.end(), nal.begin(), nal.end());
    }

    frame.payload = std::move(payload);
    frame.isKeyFrame = keyFrame;
    return frame;
}

std::vector<uint8_t> FlvMuxer::buildVideoTag(const ParsedVideoFrame& frame) const {
    std::vector<uint8_t> payload;
    if (!frame.hasData()) {
        return payload;
    }

    payload.reserve(5 + frame.payload.size());

    uint8_t header = buildVideoHeader(videoConfig_.codec, frame.isKeyFrame, false);
    payload.push_back(header);
    payload.push_back(kFlvAvcNalu);
    payload.push_back(0x00);
    payload.push_back(0x00);
    payload.push_back(0x00);
    payload.insert(payload.end(), frame.payload.begin(), frame.payload.end());
    return payload;
}

std::vector<uint8_t> FlvMuxer::buildAudioTag(const uint8_t* data, size_t size) const {
    std::vector<uint8_t> payload;
    if (data == nullptr || size == 0) {
        return payload;
    }
    payload.reserve(2 + size);
    const auto header = buildAudioHeader(audioConfig_, false);
    payload.push_back(header[0]);
    payload.push_back(header[1]);
    payload.insert(payload.end(), data, data + size);
    return payload;
}

std::array<uint8_t, 2> FlvMuxer::buildAudioHeader(const AudioConfig& /*config*/, bool isSequence) {
    std::array<uint8_t, 2> header{};
    header[0] = static_cast<uint8_t>((kFlvSoundFormatAac & 0x0F) << 4);
    header[0] |= static_cast<uint8_t>((kFlvSoundRate44k & 0x03) << 2);
    header[0] |= static_cast<uint8_t>((kFlvSoundSize16Bit & 0x01) << 1);
    header[0] |= static_cast<uint8_t>(kFlvSoundTypeStereo & 0x01);
    header[1] = static_cast<uint8_t>(isSequence ? 0x00 : 0x01);
    return header;
}

uint8_t FlvMuxer::buildVideoHeader(VideoCodecId codec,
                                    bool isKeyFrame,
                                    bool isSequence) {
    uint8_t header = 0;
    header |= static_cast<uint8_t>((isKeyFrame ? kFlvVideoFrameKey : kFlvVideoFrameInter) << 4);
    header |= static_cast<uint8_t>((codec == VideoCodecId::kH264 ? 7 : 12) & 0x0F);
    return header;
}

std::vector<uint8_t> FlvMuxer::buildAvcDecoderConfigurationRecord() const {
    std::vector<uint8_t> record;
    record.reserve(11 + sps_.size() + pps_.size());
    record.push_back(0x01);
    record.push_back(sps_.size() >= 2 ? sps_[1] : 0);
    record.push_back(sps_.size() >= 3 ? sps_[2] : 0);
    record.push_back(sps_.size() >= 4 ? sps_[3] : 0);
    record.push_back(0xFF);  // lengthSizeMinusOne (4 bytes)

    record.push_back(0xE1);  // numOfSequenceParameterSets
    record.push_back(static_cast<uint8_t>((sps_.size() >> 8) & 0xFF));
    record.push_back(static_cast<uint8_t>(sps_.size() & 0xFF));
    record.insert(record.end(), sps_.begin(), sps_.end());

    record.push_back(0x01);  // numOfPictureParameterSets
    record.push_back(static_cast<uint8_t>((pps_.size() >> 8) & 0xFF));
    record.push_back(static_cast<uint8_t>(pps_.size() & 0xFF));
    record.insert(record.end(), pps_.begin(), pps_.end());
    return record;
}

std::vector<uint8_t> FlvMuxer::buildHevcDecoderConfigurationRecord() const {
    std::vector<uint8_t> record;
    if (sps_.empty()) {
        return record;
    }

    std::vector<uint8_t> rbsp = ToRbsp(sps_);
    BitReader reader(rbsp);

    reader.readBits(4);  // sps_video_parameter_set_id
    uint32_t maxSubLayersMinus1 = reader.readBits(3);
    bool temporalIdNested = reader.readBit() == 1;

    uint32_t generalProfileSpace = reader.readBits(2);
    uint32_t generalTierFlag = reader.readBit();
    uint32_t generalProfileIdc = reader.readBits(5);

    uint32_t generalProfileCompatibilityFlags = 0;
    for (int i = 0; i < 32; ++i) {
        generalProfileCompatibilityFlags = (generalProfileCompatibilityFlags << 1) | reader.readBit();
    }

    uint64_t generalConstraintIndicatorFlags = 0;
    for (int i = 0; i < 48; ++i) {
        generalConstraintIndicatorFlags = (generalConstraintIndicatorFlags << 1) | reader.readBit();
    }

    uint32_t generalLevelIdc = reader.readBits(8);

    std::vector<uint8_t> subLayerProfilePresent(maxSubLayersMinus1);
    std::vector<uint8_t> subLayerLevelPresent(maxSubLayersMinus1);
    for (uint32_t i = 0; i < maxSubLayersMinus1; ++i) {
        subLayerProfilePresent[i] = static_cast<uint8_t>(reader.readBit());
        subLayerLevelPresent[i] = static_cast<uint8_t>(reader.readBit());
    }

    if (maxSubLayersMinus1 > 0) {
        for (uint32_t i = maxSubLayersMinus1; i < 8; ++i) {
            reader.readBits(2);
        }
    }

    for (uint32_t i = 0; i < maxSubLayersMinus1; ++i) {
        if (subLayerProfilePresent[i]) {
            reader.readBits(2);
            reader.readBits(1);
            reader.readBits(5);
            for (int j = 0; j < 32; ++j) reader.readBit();
            for (int j = 0; j < 48; ++j) reader.readBit();
        }
        if (subLayerLevelPresent[i]) {
            reader.readBits(8);
        }
    }

    reader.readUE();  // sps_seq_parameter_set_id
    uint32_t chromaFormatIdc = reader.readUE();
    if (chromaFormatIdc == 3) {
        reader.readBit();
    }

    reader.readUE();  // pic_width_in_luma_samples
    reader.readUE();  // pic_height_in_luma_samples

    if (reader.readBit()) {
        reader.readUE();
        reader.readUE();
        reader.readUE();
        reader.readUE();
    }

    uint32_t bitDepthLumaMinus8 = reader.readUE();
    uint32_t bitDepthChromaMinus8 = reader.readUE();

    record.reserve(38 + vps_.size() + sps_.size() + pps_.size());
    record.push_back(0x01);
    record.push_back(static_cast<uint8_t>((generalProfileSpace << 6) |
                                          (generalTierFlag << 5) |
                                          (generalProfileIdc & 0x1F)));
    record.push_back(static_cast<uint8_t>((generalProfileCompatibilityFlags >> 24) & 0xFF));
    record.push_back(static_cast<uint8_t>((generalProfileCompatibilityFlags >> 16) & 0xFF));
    record.push_back(static_cast<uint8_t>((generalProfileCompatibilityFlags >> 8) & 0xFF));
    record.push_back(static_cast<uint8_t>(generalProfileCompatibilityFlags & 0xFF));

    for (int shift = 40; shift >= 0; shift -= 8) {
        record.push_back(static_cast<uint8_t>((generalConstraintIndicatorFlags >> shift) & 0xFF));
    }
    record.push_back(static_cast<uint8_t>(generalLevelIdc & 0xFF));

    uint16_t minSpatialSegmentation = 0x0FFF;
    record.push_back(static_cast<uint8_t>((0xF0) | ((minSpatialSegmentation >> 8) & 0x0F)));
    record.push_back(static_cast<uint8_t>(minSpatialSegmentation & 0xFF));

    record.push_back(static_cast<uint8_t>((0xFC) | 0x00));
    record.push_back(static_cast<uint8_t>((0xFC) | (chromaFormatIdc & 0x03)));
    record.push_back(static_cast<uint8_t>((0xF8) | (bitDepthLumaMinus8 & 0x07)));
    record.push_back(static_cast<uint8_t>((0xF8) | (bitDepthChromaMinus8 & 0x07)));

    record.push_back(0x00);
    record.push_back(0x00);

    uint8_t temporalLayers = static_cast<uint8_t>(std::min<uint32_t>(maxSubLayersMinus1 + 1, 8) - 1);
    uint8_t flagsByte = static_cast<uint8_t>((0 << 6) | (temporalLayers << 3) | (temporalIdNested ? 1 << 2 : 0) | 0x03);
    record.push_back(flagsByte);
    record.push_back(0x03);

    auto appendNal = [&record](uint8_t nalType, const std::vector<uint8_t>& nal) {
        record.push_back(static_cast<uint8_t>((1 << 7) | (nalType & 0x3F)));
        record.push_back(0x00);
        record.push_back(0x01);
        record.push_back(static_cast<uint8_t>((nal.size() >> 8) & 0xFF));
        record.push_back(static_cast<uint8_t>(nal.size() & 0xFF));
        record.insert(record.end(), nal.begin(), nal.end());
    };

    appendNal(kVpsNalTypeH265, vps_);
    appendNal(kSpsNalTypeH265, sps_);
    appendNal(kPpsNalTypeH265, pps_);

    return record;
}

}  // namespace astra
