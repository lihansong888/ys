import requests
import re

def get_content(url):
    headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"}
    try:
        r = requests.get(url, headers=headers, timeout=15)
        r.encoding = 'utf-8'
        return r.text if r.status_code == 200 else ""
    except:
        return ""

def main():
    final_output = []

    # --- 1. 精品频道 (保留目前最稳的源) ---
    final_output.append("精品频道,#genre#")
    adult_content = get_content("http://adultiptv.net/chs.m3u")
    if adult_content:
        name = ""
        for line in adult_content.split('\n'):
            line = line.strip()
            if "#EXTINF" in line: name = line.split(',')[-1].strip()
            elif line.startswith("http") and name:
                final_output.append(f"{name},{line}")
                name = ""

    # --- 2. 核心直播 (抓取你提供的最新有效源) ---
    final_output.append("核心直播,#genre#")
    # 这个源是你测试过可用的最新地址
    migu_source = get_content("https://gitee.com/yimi321/tv/raw/master/tv.png")
    if migu_source:
        for line in migu_source.split('\n'):
            line = line.strip()
            # 过滤掉非频道行，只保留符合 TVBox 格式的内容
            if "," in line and "http" in line:
                # 只抓取 CCTV 和 卫视，防止列表太冗余
                if any(x in line for x in ["CCTV", "卫视", "咪咕"]):
                    final_output.append(line)

    # 写入文件
    with open("migu.txt", "w", encoding="utf-8") as f:
        f.write("\n".join(final_output))
    print("有效源已同步完成")

if __name__ == "__main__":
    main()
