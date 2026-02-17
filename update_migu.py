import requests

def main():
    # 建立你的专属频道列表
    # 这些是根据你提供的稳定源格式整理的
    channels = [
        ("CCTV-1", "http://209.141.59.47:82/live/cctv1.m3u8?jsbt=1771331451&jsbk=700356ed21677d571370af3165c9a0b0"),
        ("CCTV-5", "http://209.141.59.47:82/live/cctv5.m3u8?jsbt=1771331451&jsbk=700356ed21677d571370af3165c9a0b0"),
        ("CCTV-5+", "http://209.141.59.47:82/live/cctv5plus.m3u8?jsbt=1771331451&jsbk=700356ed21677d571370af3165c9a0b0"),
        ("CCTV-8", "http://209.141.59.47:82/live/cctv8.m3u8?jsbt=1771331451&jsbk=700356ed21677d571370af3165c9a0b0"),
        ("江苏卫视", "http://209.141.59.47:82/live/jiangsu.m3u8?jsbt=1771331451&jsbk=700356ed21677d571370af3165c9a0b0")
    ]
    
    output = []
    
    # 分类 1：精品频道 (你测试有效的 adultiptv 源)
    output.append("精品频道,#genre#")
    # 这里会自动去抓取你之前那个出画面的源
    try:
        r = requests.get("http://adultiptv.net/chs.m3u", timeout=10)
        if r.status_code == 200:
            name = ""
            for line in r.text.split('\n'):
                if "#EXTINF" in line: name = line.split(',')[-1].strip()
                elif "http" in line and name:
                    output.append(f"{name},{line.strip()}")
                    name = ""
    except: pass

    # 分类 2：核心直播 (搬运你提供的这组稳定源)
    output.append("核心直播,#genre#")
    for name, url in channels:
        output.append(f"{name},{url}")

    with open("migu.txt", "w", encoding="utf-8") as f:
        f.write("\n".join(output))
    print("稳定源已整合")

if __name__ == "__main__":
    main()
